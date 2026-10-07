/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.ftm.garments.sku

/**
 * Builds an SKU code from a rule's ordered segments. Pure: no database access — the caller passes the
 * segments and a closure that resolves a FEATURE segment to its ProductFeature.
 *
 * Segment types (FtmSkuRuleSegment.segmentTypeId):
 *   LITERAL  emit literalText
 *   INPUT    emit the caller's value for inputName, verbatim
 *   TEXT     emit the value after textOps, applied in order: LEFT:n | RIGHT:n | UPPER | TRIM (Excel semantics)
 *   FEATURE  emit the idCode of the ProductFeature the closure resolves for (productFeatureTypeId, inputName)
 * A missing value or an unresolved feature REFUSES the whole code (SkuRefusedException) — never a partial code.
 * The canonical form drops only the dashes the rule itself emits as LITERAL separators; a dash inside a looked-up
 * value or an input is part of the code (the workbook counts it, and its LEN check fails on it).
 */
final class SkuComposer {

    static final Set<String> SEGMENT_TYPES = ['LITERAL', 'INPUT', 'TEXT', 'FEATURE'] as Set

    private SkuComposer() { }

    /**
     * @param segments ordered segment maps (segmentTypeId, literalText, inputName, productFeatureTypeId, textOps)
     * @param values inputName to value
     * @param featureFor (productFeatureTypeId, inputName) to a map with productFeatureId and idCode, or null
     * @return [code, canonical (code without the rule's literal dashes), featureIds (in segment order),
     *          attributes (inputName to value, for INPUT and TEXT segments)]
     */
    static Map compose(List<Map> segments, Map<String, String> values, Closure<Map> featureFor) {
        StringBuilder code = new StringBuilder()
        StringBuilder canonical = new StringBuilder()
        List<String> featureIds = []
        Map<String, String> attributes = [:]
        for (Map seg in segments) {
            String type = seg.segmentTypeId
            String name = seg.inputName
            String piece
            switch (type) {
                case 'LITERAL':
                    piece = seg.literalText ?: ''
                    code.append(piece)
                    canonical.append(piece.replace('-', ''))
                    continue
                case 'INPUT':
                    piece = required(values, name)
                    attributes[name] = values[name]
                    break
                case 'TEXT':
                    piece = applyTextOps(required(values, name), seg.textOps as String)
                    attributes[name] = values[name]
                    break
                case 'FEATURE':
                    Map feature = featureFor(seg.productFeatureTypeId as String, name)
                    if (!feature) {
                        throw new SkuRefusedException("No [${seg.productFeatureTypeId}] feature for ${name} = [${values?.get(name)}]")
                    }
                    piece = feature.idCode ?: ''
                    featureIds << (feature.productFeatureId as String)
                    break
                default:
                    throw new SkuRefusedException("Unknown segment type [${type}] at sequence ${seg.sequenceNum}")
            }
            code.append(piece)
            canonical.append(piece)
        }
        return [code: code.toString(), canonical: canonical.toString(), featureIds: featureIds, attributes: attributes]
    }

    /** Ops in order, separated by '|': LEFT:n, RIGHT:n, UPPER, TRIM (spaces only, inner runs collapsed, as Excel). */
    static String applyTextOps(String value, String ops) {
        String s = value
        for (String op in (ops ?: '').split(/\|/).findAll { String o -> o }) {
            List<String> p = op.split(':') as List
            switch (p[0]) {
                case 'LEFT':
                    s = s.take(p[1] as int)
                    break
                case 'RIGHT':
                    int n = p[1] as int
                    s = n >= s.length() ? s : s.substring(s.length() - n)
                    break
                case 'UPPER':
                    s = s.toUpperCase(Locale.ROOT)
                    break
                case 'TRIM':
                    s = s.replaceAll(/ +/, ' ').replaceAll(/^ | $/, '')
                    break
                default:
                    throw new SkuRefusedException("Unknown text op [${op}]")
            }
        }
        return s
    }

    /** A code TYPED by a person, in either form, normalised for lookup: dashes removed, upper case. */
    static String canonical(String code) {
        return code == null ? null : code.replace('-', '').trim().toUpperCase(Locale.ROOT)
    }

    private static String required(Map<String, String> values, String name) {
        String v = values?.get(name)
        if (v == null || v.isEmpty()) {
            throw new SkuRefusedException("Missing value for ${name}")
        }
        return v
    }

}
