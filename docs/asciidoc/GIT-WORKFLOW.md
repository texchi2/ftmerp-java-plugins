# FTM Git Workflow Reference

## Daily Commands

```bash
# Sync with remote (same branch — run every morning)
git pull --rebase

# Get security fixes from main into feature branch
git fetch origin && git rebase origin/main

# Push after any rebase (history was rewritten)
git push --force-with-lease --no-verify

# WIP handover to partner
git add -A
git commit -m "WIP: [description] — handover to [Tex/Kona]" --no-verify
git push --no-verify

## When to Use What



|Situation                                  |Command                                                       |
|-------------------------------------------|--------------------------------------------------------------|
|Daily start, no divergence                 |`git pull --rebase`                                           |
|Get main security fixes into feature branch|`git fetch origin && git rebase origin/main`                  |
|After any rebase                           |`git push --force-with-lease --no-verify`                     |
|WIP handover                               |`git add -A && git commit --no-verify && git push --no-verify`|


## Key Rules
	•	git pull --rebase = git fetch + git rebase (linear history)
	•	Run ftm-sync alias at start of every session
	•	Never --force alone — always --force-with-lease
	•	All machines: git config --global pull.rebase true

## Alias (add to ~/.bashrc or ~/.zshrc)

alias ftm-sync='git fetch origin && git rebase origin/main'
alias ftm-push='git push --force-with-lease origin feature/ftm-garments --no-verify'
alias ftm-status='git -C /home/texchi/development/ofbiz-framework status --short && git -C /home/texchi/development/ofbiz-plugins status --short'
