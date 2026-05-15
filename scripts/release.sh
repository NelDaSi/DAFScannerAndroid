#!/bin/bash

# Configuration
GRADLE_FILE="app/build.gradle.kts"

# Colors for UI
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}   DAF Scanner - Release Automator    ${NC}"
echo -e "${BLUE}======================================${NC}"

# 1. Get current versions from gradle file
CURRENT_VERSION_NAME=$(grep "versionName =" $GRADLE_FILE | sed 's/.*"\(.*\)".*/\1/')
CURRENT_VERSION_CODE=$(grep "versionCode =" $GRADLE_FILE | sed 's/.*= \(.*\)/\1/')

echo -e "${YELLOW}Current Project Version: ${NC}$CURRENT_VERSION_NAME ($CURRENT_VERSION_CODE)"

# 2. Get latest tag from git
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "None")
echo -e "${YELLOW}Latest Git Tag:        ${NC}$LATEST_TAG"
echo -e "${BLUE}--------------------------------------${NC}"

# 3. Ask for new version name
read -p "Enter NEW Version Name (e.g. 1.2.0) [Current: $CURRENT_VERSION_NAME]: " NEW_VERSION_NAME
NEW_VERSION_NAME=${NEW_VERSION_NAME:-$CURRENT_VERSION_NAME}

# 4. Ask for new version code
SUGGESTED_CODE=$((CURRENT_VERSION_CODE + 1))
read -p "Enter NEW Version Code (integer) [Suggested: $SUGGESTED_CODE]: " NEW_VERSION_CODE
NEW_VERSION_CODE=${NEW_VERSION_CODE:-$SUGGESTED_CODE}

echo -e "${BLUE}--------------------------------------${NC}"
echo -e "Preparing to release ${GREEN}v$NEW_VERSION_NAME ($NEW_VERSION_CODE)${NC}..."

# 5. Update the Gradle file
# Using perl because it's more reliable than sed across different OS for in-place edits of .kts files
perl -i -pe "s/versionName = \".*\"/versionName = \"$NEW_VERSION_NAME\"/" $GRADLE_FILE
perl -i -pe "s/versionCode = \d+/versionCode = $NEW_VERSION_CODE/" $GRADLE_FILE

echo -e "${GREEN}✔ Updated $GRADLE_FILE${NC}"

# 6. Git Operations
echo -e "${YELLOW}Committing and Tagging...${NC}"
git add $GRADLE_FILE
git commit -m "chore: bump version to $NEW_VERSION_NAME ($NEW_VERSION_CODE)"
git tag "v$NEW_VERSION_NAME"

# 7. Push
read -p "Push changes and tag to GitHub now? (y/n): " PUSH_CONFIRM
if [[ $PUSH_CONFIRM =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Pushing to origin...${NC}"
    git push origin $(git rev-parse --abbrev-ref HEAD)
    git push origin "v$NEW_VERSION_NAME"
    echo -e "${GREEN}🚀 Successfully pushed v$NEW_VERSION_NAME! GitHub Action started.${NC}"
else
    echo -e "${YELLOW}Changes committed and tagged locally, but NOT pushed.${NC}"
fi

echo -e "${BLUE}======================================${NC}"
