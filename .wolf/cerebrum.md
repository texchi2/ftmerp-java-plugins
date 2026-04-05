# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-04-05

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

## Key Learnings

- **Project:** ofbiz-plugins
- **Description:** Apache OFBiz is an open source product for the automation of enterprise processes.

## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->

## Decision Log

<!-- Significant technical decisions with rationale. Why X was chosen over Y. -->

## Do-Not-Repeat (OFBiz-Specific)
- Never use runEvent() — does not exist in OFBiz Groovy
- Never use closure-based HttpServletResponse — use context.response
- Never return success([response: closure]) — return "success" string for type=none
- Never login to webtools then test ftm-wifi endpoints — different session context
- Never add PARAMETER think to Modelfile — think is an API field not Modelfile param
- Always .toString() on ALL JDBC setString() parameters
- Always declare variables before try block if used after finally

## Do-Not-Repeat (File Download Pattern)
[2026-4-5]
- NEVER use <hyperlink> inside a Form widget for file downloads - generates JS form submit that browsers block
- ALWAYS use <link> in Screen widget (FtmWifiScreens.xml) for file downloads
- <link> creates a standalone hidden form that browsers handle correctly as download
- Example: <link text="Export CSV" target="ExportAuthorizedUsersCsv" style="buttontext" url-mode="intra-app"/>
- OFBiz <form type="single"> always generates method="post" with JS submit - never use for downloads

