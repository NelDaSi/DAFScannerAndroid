#!/bin/bash

# Configuration
GRADLE_FILE="app/build.gradle.kts"

# Colors for UI
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}   DAF Scanner - Release Automator    ${NC}"
echo -e "${BLUE}======================================${NC}"

# 0. Check for uncommitted changes
# Refresh the index to avoid false positives
git update-index -q --refresh
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}Error: You have uncommitted changes in tracked files:${NC}"
    git diff-index --name-only HEAD --
    echo -e "\nPlease commit or stash them before running the release script."
    exit 1
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo -e "${YELLOW}Current Branch: ${NC}$CURRENT_BRANCH"

# 1. Ask for Release Type
echo -e "\n${BLUE}Release Type:${NC}"
echo "1) Standard (Release from current branch: $CURRENT_BRANCH)"
echo "2) Production (Merge $CURRENT_BRANCH into main and release from main)"
read -p "Select option (1-2): " RELEASE_TYPE

if [[ "$RELEASE_TYPE" == "2" ]]; then
    if [[ "$CURRENT_BRANCH" == "main" ]]; then
        echo -e "${YELLOW}You are already on main. Proceeding with standard release...${NC}"
        TARGET_BRANCH="main"
    else
        TARGET_BRANCH="main"
        echo -e "${YELLOW}Switching to $TARGET_BRANCH and merging $CURRENT_BRANCH...${NC}"
        git checkout $TARGET_BRANCH || exit 1
        git merge $CURRENT_BRANCH || exit 1
    fi
else
    TARGET_BRANCH=$CURRENT_BRANCH
fi

# 2. Get current versions from gradle file
CURRENT_VERSION_NAME=$(grep "versionName =" $GRADLE_FILE | sed 's/.*"\(.*\)".*/\1/')
CURRENT_VERSION_CODE=$(grep "versionCode =" $GRADLE_FILE | sed 's/.*= \(.*\)/\1/')

echo -e "\n${YELLOW}Current Project Version: ${NC}$CURRENT_VERSION_NAME ($CURRENT_VERSION_CODE)"

# 3. Get latest tag from git
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "None")
echo -e "${YELLOW}Latest Git Tag:        ${NC}$LATEST_TAG"
echo -e "${BLUE}--------------------------------------${NC}"

# 4. Ask for new version name
read -p "Enter NEW Version Name (e.g. 1.2.0) [Current: $CURRENT_VERSION_NAME]: " NEW_VERSION_NAME
NEW_VERSION_NAME=${NEW_VERSION_NAME:-$CURRENT_VERSION_NAME}

# 5. Ask for new version code
SUGGESTED_CODE=$((CURRENT_VERSION_CODE + 1))
read -p "Enter NEW Version Code (integer) [Suggested: $SUGGESTED_CODE]: " NEW_VERSION_CODE
NEW_VERSION_CODE=${NEW_VERSION_CODE:-$SUGGESTED_CODE}

echo -e "${BLUE}--------------------------------------${NC}"
echo -e "Preparing to release ${GREEN}v$NEW_VERSION_NAME ($NEW_VERSION_CODE)${NC} on branch ${BLUE}$TARGET_BRANCH${NC}..."

# 6. Update the Gradle file
perl -i -pe "s/versionName = \".*\"/versionName = \"$NEW_VERSION_NAME\"/" $GRADLE_FILE
perl -i -pe "s/versionCode = \d+/versionCode = $NEW_VERSION_CODE/" $GRADLE_FILE

echo -e "${GREEN}✔ Updated $GRADLE_FILE${NC}"

# 7. Git Operations
echo -e "${YELLOW}Committing and Tagging...${NC}"
git add $GRADLE_FILE
git commit -m "chore: bump version to $NEW_VERSION_NAME ($NEW_VERSION_CODE)"
git tag "v$NEW_VERSION_NAME"

# 8. Push
read -p "Push changes and tag to GitHub now? (y/n): " PUSH_CONFIRM
if [[ $PUSH_CONFIRM =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Pushing to origin...${NC}"
    git push origin $TARGET_BRANCH
    git push origin "v$NEW_VERSION_NAME"

    if [[ "$RELEASE_TYPE" == "2" && "$CURRENT_BRANCH" != "main" ]]; then
        echo -e "${YELLOW}Syncing $CURRENT_BRANCH back with main...${NC}"
        git checkout $CURRENT_BRANCH
        git merge $TARGET_BRANCH
        git push origin $CURRENT_BRANCH
    fi

    echo -e "${GREEN}🚀 Successfully released v$NEW_VERSION_NAME! GitHub Action started.${NC}"
else
    echo -e "${YELLOW}Changes committed locally on $TARGET_BRANCH, but NOT pushed.${NC}"
fi

echo -e "${BLUE}======================================${NC}"
