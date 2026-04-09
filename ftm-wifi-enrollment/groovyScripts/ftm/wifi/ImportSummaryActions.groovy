context.addedCount   = request.getAttribute("addedCount")   ?: 0
context.updatedCount = request.getAttribute("updatedCount") ?: 0
context.skippedCount = request.getAttribute("skippedCount") ?: 0
context.errorList    = request.getAttribute("errorList")    ?: []
context.warnings     = request.getAttribute("warnings")     ?: []
