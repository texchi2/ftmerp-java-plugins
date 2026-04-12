# /simple — Fast Execution Mode

**Trigger:** User prefixes task with `/simple`

**Rules for gemma4 when /simple is active:**
1. NO anatomy.md scan
2. NO deep planning or chain-of-thought
3. NO .wolf memory update (skip it)
4. Use Bash tool DIRECTLY — execute immediately
5. Maximum 2 tool calls: one to execute, one to verify
6. If task is a file write → use Bash with cat heredoc
7. Report result in 3 lines max: what was done, verify output, done.

**Examples:**
- `/simple create FtmGarmentsMenus.xml with [content]`
  → Bash: cat > file << 'EOF'...EOF; grep verify; done.

- `/simple restart ofbiz`
  → Bash: pkill + rm locks + gradlew ofbiz &; done.

- `/simple check log for errors`
  → Bash: grep ERROR /tmp/ofbiz-phase8.log | head -20; done.

**What /simple is NOT for:**
- Architecture decisions
- Multi-file refactors  
- Debugging unknown errors
- Anything requiring doc-first

**Estimated time with /simple: 30 seconds vs 18 minutes**
