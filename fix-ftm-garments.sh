#!/bin/bash
# Fix ftm-garments plugin for modern OFBiz (minilang removed)
# Run this script in your ftmerp-java-plugins repository directory

set -e  # Exit on error

echo "=== Fixing ftm-garments plugin for modern OFBiz ==="

# Check we're in the right place
if [ ! -d "ftm-garments" ]; then
    echo "Error: ftm-garments directory not found!"
    echo "Please run this script from the ftmerp-java-plugins repository root"
    exit 1
fi

echo "✓ Found ftm-garments directory"

# Create missing directories
echo "Creating missing directories..."
mkdir -p ftm-garments/data
mkdir -p ftm-garments/config
mkdir -p ftm-garments/groovyScripts/product
mkdir -p ftm-garments/groovyScripts/order
echo "✓ Directories created"

# Update build.gradle
echo "Updating build.gradle (removing minilang dependency)..."
cat > ftm-garments/build.gradle << 'EOF'
/*
 * FTM Garments Plugin Build Configuration
 * Updated for modern OFBiz (minilang removed, replaced with Groovy)
 */

dependencies {
    // Core framework dependencies
    pluginLibsCompile project(':framework:base')
    pluginLibsCompile project(':framework:entity')
    pluginLibsCompile project(':framework:service')
    pluginLibsCompile project(':framework:security')

    // NOTE: framework:minilang removed in modern OFBiz
    // Use Groovy scripts instead (see groovyScripts/ directory)

    // Application dependencies for garment manufacturing
    pluginLibsCompile project(':applications:product')
    pluginLibsCompile project(':applications:order')
    pluginLibsCompile project(':applications:manufacturing')
    pluginLibsCompile project(':applications:accounting')
    pluginLibsCompile project(':applications:party')
}
EOF
echo "✓ build.gradle updated"

# Create FtmGarmentsTypeData.xml
echo "Creating FtmGarmentsTypeData.xml (seed data)..."
cat > ftm-garments/data/FtmGarmentsTypeData.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<entity-engine-xml>
    <!-- FTM Garments Seed/Type Data -->
    <!-- Security Permissions -->
    <SecurityPermission permissionId="FTM_GARMENTS_ADMIN" description="FTM Garments Administrator"/>
    <SecurityPermission permissionId="FTM_GARMENTS_CREATE" description="FTM Garments Create"/>
    <SecurityPermission permissionId="FTM_GARMENTS_UPDATE" description="FTM Garments Update"/>
    <SecurityPermission permissionId="FTM_GARMENTS_VIEW" description="FTM Garments View"/>

    <!-- Product Categories for Garments -->
    <ProductCategory productCategoryId="CASUAL_PANTS" productCategoryTypeId="CATALOG_CATEGORY">
        <categoryName>Casual Pants</categoryName>
        <description>Men's and Women's Casual Pants</description>
    </ProductCategory>

    <ProductCategory productCategoryId="CASUAL_SHIRTS" productCategoryTypeId="CATALOG_CATEGORY">
        <categoryName>Casual Shirts</categoryName>
        <description>Men's and Women's Casual Shirts</description>
    </ProductCategory>
</entity-engine-xml>
EOF
echo "✓ FtmGarmentsTypeData.xml created"

# Create FtmGarmentsDemoData.xml
echo "Creating FtmGarmentsDemoData.xml (demo data)..."
cat > ftm-garments/data/FtmGarmentsDemoData.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<entity-engine-xml>
    <!-- FTM Garments Demo Data -->
    <!-- For detailed product data including Men's Pant BOM,
         see: https://github.com/texchi2/ftmerp-java-project/blob/main/docs/FTM-GARMENTS-WORKFLOW-DATASET.md -->

    <!-- Demo data can be added here for testing -->
    <!-- Use: ./gradlew "ofbiz --load-data readers=demo" -->
</entity-engine-xml>
EOF
echo "✓ FtmGarmentsDemoData.xml created"

# Create README.md
echo "Creating README.md (comprehensive documentation)..."
cat > ftm-garments/README.md << 'EOFREADME'
# FTM Garments Plugin

Custom OFBiz plugin for FTM Garments Manufacturing ERP system.

## Overview

This plugin provides garment manufacturing-specific functionality for Apache OFBiz, including:
- Product catalog management for garments
- Bill of Materials (BOM) for pants, shirts, and other garments
- Manufacturing workflows
- Custom services for garment production

## Modern OFBiz Compatibility

**Important**: This plugin is updated for modern OFBiz versions where:
- ✅ **Groovy scripts** are used for service implementations
- ❌ **Minilang** has been removed
- ✅ **Modern dependency configuration** using `pluginLibsCompile`

## Directory Structure

```
ftm-garments/
├── build.gradle              # Plugin dependencies (NO minilang!)
├── ofbiz-component.xml        # Component configuration
├── config/                    # Configuration files
├── data/                      # Data files
│   ├── FtmGarmentsTypeData.xml    # Seed data
│   └── FtmGarmentsDemoData.xml    # Demo data
├── entitydef/                 # Entity definitions
│   └── entitymodel.xml
├── groovyScripts/            # Groovy service implementations
│   ├── product/              # Product-related services
│   └── order/                # Order-related services
├── servicedef/               # Service definitions
│   └── services.xml
└── webapp/                   # Web application
    └── ftm-garments/
```

## Quick Start

### 1. Installation

```bash
# On your rpitex system
cd ~/development
git clone git@github.com:texchi2/ftmerp-java-plugins.git ofbiz-plugins
cd ofbiz-framework
./gradlew clean loadAll
```

### 2. Verify Plugin Loaded

```bash
# Check if plugin is recognized
./gradlew projects | grep ftm-garments

# Should show: :plugins:ftm-garments
```

### 3. Load Demo Data

```bash
./gradlew "ofbiz --load-data readers=demo"
```

## Documentation

Detailed documentation available in the main repository:

- **Workflow Dataset**: Complete BOM example for men's pants
  - https://github.com/texchi2/ftmerp-java-project/blob/main/docs/FTM-GARMENTS-WORKFLOW-DATASET.md

- **BOM Deep Dive**: Detailed garment manufacturing BOM
  - https://github.com/texchi2/ftmerp-java-project/blob/main/docs/BOM-DEEP-DIVE.md

- **Learning Guide**: OFBiz concepts and workflows
  - https://github.com/texchi2/ftmerp-java-project/blob/main/docs/OFBIZ-LEARNING-GUIDE.md

- **Glossary**: ERP and manufacturing terminology
  - https://github.com/texchi2/ftmerp-java-project/blob/main/docs/ERP-MANUFACTURING-GLOSSARY.md

## Migration from Minilang to Groovy

If you have old minilang services, convert them to Groovy:

### Old (Minilang - REMOVED):
```xml
<service name="createFtmProduct" engine="simple"
        location="component://ftm-garments/minilang/ProductServices.xml">
```

### New (Groovy - CURRENT):
```xml
<service name="createFtmProduct" engine="groovy"
        location="component://ftm-garments/groovyScripts/product/CreateProduct.groovy">
```

### Example Groovy Service:
```groovy
// groovyScripts/product/CreateProduct.groovy
import org.apache.ofbiz.entity.GenericValue

def delegator = dctx.delegator
def productId = parameters.productId

def product = delegator.makeValue("Product", [
    productId: productId,
    productTypeId: parameters.productTypeId,
    internalName: parameters.internalName
])

delegator.create(product)

return success([productId: productId])
```

## Troubleshooting

### Build Error: "Could not resolve project :framework:minilang"

**Solution**: Update to latest version - minilang dependency removed from `build.gradle`

### Service Engine Errors

If you see minilang-related errors, check:
1. `servicedef/services.xml` - change `engine="simple"` to `engine="groovy"`
2. Move service implementation from `minilang/` to `groovyScripts/`

## Development

### Adding New Services

1. Define service in `servicedef/services.xml`
2. Implement in Groovy: `groovyScripts/[module]/[ServiceName].groovy`
3. Test with WebTools: https://localhost:8443/webtools

### Adding New Entities

1. Define entity in `entitydef/entitymodel.xml`
2. Run: `./gradlew loadAll` to create tables
3. Verify in WebTools: Entity Data Maintenance

## License

Apache License 2.0

## Support

For issues or questions:
- GitHub Issues: https://github.com/texchi2/ftmerp-java-plugins/issues
- Main Project: https://github.com/texchi2/ftmerp-java-project
EOFREADME
echo "✓ README.md created"

echo ""
echo "=== ✅ All fixes applied successfully! ==="
echo ""
echo "Summary of changes:"
echo "  • Updated build.gradle (removed minilang dependency)"
echo "  • Created data/FtmGarmentsTypeData.xml (seed data)"
echo "  • Created data/FtmGarmentsDemoData.xml (demo data)"
echo "  • Created README.md (comprehensive guide)"
echo "  • Created groovyScripts directories for modern services"
echo ""
echo "Next steps:"
echo "  1. Review the changes: git status"
echo "  2. Commit: git add -A && git commit -m 'Fix ftm-garments for modern OFBiz'"
echo "  3. Push: git push"
echo "  4. Test build: cd ../ofbiz-framework && ./gradlew clean loadAll"
echo ""
echo "The build errors should now be resolved!"
