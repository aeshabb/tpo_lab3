import re

with open('src/test/java/github/GitHubRCTest.java', 'r') as f:
    text = f.read()

# REMOVE ALL Thread.sleep calls regardless of what breaks
text = re.sub(r'Thread\.sleep\(\d+\);\s*', '', text)
text = re.sub(r'catch \(InterruptedException e\) \{.*?\}', '', text, flags=re.DOTALL)
text = re.sub(r'try\s*\{\s*\}\s*', '', text, flags=re.DOTALL) # clean up try{} blocks left behind

with open('src/test/java/github/GitHubRCTest.java', 'w') as f:
    f.write(text)

