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
