import re

with open("src/test/java/github/GitHubRCTest.java", "r") as f:
    content = f.read()

# Remove all Thread.sleep
content = re.sub(r'^\s*Thread\.sleep\(.*?\);\s*$', '', content, flags=re.MULTILINE)
content = re.sub(r'^\s*try\s*\{\s*Thread\.sleep\(.*?\);\s*\}\s*catch\s*\(InterruptedException\s*e\)\s*\{\s*\}', '', content, flags=re.MULTILINE)


print("Rewritten.")
