import os
import re

with open("src/test/java/github/GitHubRCTest.java", "r") as f:
    text = f.read()

# Just extract methods or output completely new file.
