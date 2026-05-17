#!/bin/bash

# Configuration
GRADLE_FILE="app/build.gradle.kts"

# Colors for UI
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

show_header() {
    clear
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}   DAF Scanner - Release Automator    ${NC}"
    echo -e "${BLUE}======================================${NC}"
}

get_git_status() {
    # Refresh index
    git update-index -q --refresh

    # Tracked changes
    UNCOMMITTED_COUNT=$(git diff-index --name-only HEAD -- | wc -l | xargs)
    # Untracked files
    UNTRACKED_COUNT=$(git ls-files --others --exclude-standard | wc -l | xargs)
    TOTAL_CHANGES=$((UNCOMMITTED_COUNT + UNTRACKED_COUNT))

    # Ahead/Behind status
    git fetch -q origin $(git rev-parse --abbrev-ref HEAD) 2>/dev/null
    LOCAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    REMOTE_BRANCH="origin/$LOCAL_BRANCH"

    if git rev-parse --verify "$REMOTE_BRANCH" >/dev/null 2>&1; then
        STATUS_INFO=$(git rev-list --left-right --count HEAD...$REMOTE_BRANCH)
        AHEAD=$(echo $STATUS_INFO | awk '{print $1}')
        BEHIND=$(echo $STATUS_INFO | awk '{print $2}')
    else
        AHEAD="?"
        BEHIND="?"
    fi
}

main_menu() {
    show_header
    get_git_status

    echo -e "${CYAN}--- Status ---${NC}"
    echo -e "Branch:        ${YELLOW}$LOCAL_BRANCH${NC}"

    if [ "$TOTAL_CHANGES" -eq "0" ]; then
        echo -e "Working Tree:  ${GREEN}Clean${NC}"
    else
        echo -e "Working Tree:  ${RED}$TOTAL_CHANGES uncommitted changes${NC}"
        if [ "$UNTRACKED_COUNT" -gt "0" ]; then
            echo -e "               ($UNTRACKED_COUNT untracked files)"
        fi
    fi

    if [ "$AHEAD" != "?" ]; then
        echo -e "Sync:          ${YELLOW}$AHEAD ahead${NC}, ${YELLOW}$BEHIND behind${NC} remote"
    fi
    echo -e "${BLUE}--------------${NC}\n"

    if [ "$TOTAL_CHANGES" -gt "0" ]; then
        echo -e "${YELLOW}Changes detected! You must have a clean tree to release.${NC}"
        echo "1) View changes (git status)"
        echo "2) Commit all changes now"
        echo "q) Exit"
        read -p "Selection: " choice

        case $choice in
            1)
                git status
                read -p "Press Enter to return to menu..."
                main_menu
                ;;
            2)
                read -p "Enter commit message: " msg
                if [ -n "$msg" ]; then
                    git add .
                    git commit -m "$msg"
                    main_menu
                else
                    echo -e "${RED}Commit message cannot be empty!${NC}"
                    sleep 2
                    main_menu
                fi
                ;;
            q|Q) exit 0 ;;
            *) main_menu ;;
        esac
    else
        echo -e "${GREEN}Ready for release!${NC}"
        echo "1) Standard Release (from $LOCAL_BRANCH)"
        echo "2) Production Release (merge $LOCAL_BRANCH into main)"
        echo "q) Exit"
        read -p "Selection: " choice

        case $choice in
            1) start_release "standard" ;;
            2) start_release "production" ;;
            q|Q) exit 0 ;;
            *) main_menu ;;
        esac
    fi
}

start_release() {
    TYPE=$1
    CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

    if [ "$TYPE" == "production" ]; then
        if [ "$CURRENT_BRANCH" == "main" ]; then
            TARGET_BRANCH="main"
        else
            TARGET_BRANCH="main"
            echo -e "${YELLOW}Switching to main and merging $CURRENT_BRANCH...${NC}"
            git checkout main || exit 1
            git merge $CURRENT_BRANCH || exit 1
        fi
    else
        TARGET_BRANCH=$CURRENT_BRANCH
    fi

    # Get current versions
    CURRENT_NAME=$(grep "versionName =" $GRADLE_FILE | sed 's/.*"\(.*\)".*/\1/')
    CURRENT_CODE=$(grep "versionCode =" $GRADLE_FILE | sed 's/.*= \(.*\)/\1/')
    LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "None")

    echo -e "\n${CYAN}Release Configuration:${NC}"
    echo -e "Current: v$CURRENT_NAME ($CURRENT_CODE) | Tag: $LATEST_TAG"

    read -p "Enter NEW Version Name [Current: $CURRENT_NAME]: " NEW_NAME
    NEW_NAME=${NEW_NAME:-$CURRENT_NAME}

    SUGGESTED_CODE=$((CURRENT_CODE + 1))
    read -p "Enter NEW Version Code [Suggested: $SUGGESTED_CODE]: " NEW_CODE
    NEW_CODE=${NEW_CODE:-$SUGGESTED_CODE}

    echo -e "\n${YELLOW}Building v$NEW_NAME ($NEW_CODE) on $TARGET_BRANCH...${NC}"

    # Update Gradle
    perl -i -pe "s/versionName = \".*\"/versionName = \"$NEW_NAME\"/" $GRADLE_FILE
    perl -i -pe "s/versionCode = \d+/versionCode = $NEW_CODE/" $GRADLE_FILE

    # Git commit/tag
    git add $GRADLE_FILE
    git commit -m "chore: bump version to $NEW_NAME ($NEW_CODE)"
    git tag "v$NEW_NAME"

    read -p "Push to GitHub now? (y/n): " do_push
    if [[ $do_push =~ ^[Yy]$ ]]; then
        git push origin $TARGET_BRANCH
        git push origin "v$NEW_NAME"

        if [ "$TYPE" == "production" ] && [ "$CURRENT_BRANCH" != "main" ]; then
            echo -e "${YELLOW}Syncing $CURRENT_BRANCH back...${NC}"
            git checkout $CURRENT_BRANCH
            git merge main
            git push origin $CURRENT_BRANCH
        fi
        echo -e "${GREEN}🚀 Release v$NEW_NAME complete!${NC}"
    else
        echo -e "${YELLOW}Release tagged locally. Don't forget to push!${NC}"
    fi
}

main_menu
