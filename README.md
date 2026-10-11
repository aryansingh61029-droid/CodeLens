This application acts as an automated, structural plagiarism detector for source code (Java, Python, and C/C++). Instead of just comparing text line-by-line, it understands the underlying structure of the code, making it impossible to fool with basic copy-pasting tricks.

Here is exactly what it does when you feed it code:

Ignores the Fluff: It immediately strips away all whitespace, tabs, and comments. It doesn't care how the code is formatted or what notes the programmer left behind.

Looks Past Variable Names: It normalizes the code structure, meaning if Student A writes int myCounter = 0; and Student B copies it but writes int x = 0;, the app recognizes the logic is identical.

Fingerprints the Logic: Using an algorithm called Winnowing (the same underlying logic used by Stanford's MOSS), it breaks the cleaned-up code into small chunks, hashes them, and creates a unique mathematical "fingerprint" of the program's structural flow.

Calculates Exact Similarity: It compares the fingerprints of two files and calculates a precise overlap percentage (Jaccard Similarity), returning a verdict of Low, Medium, or High risk.

Ranks Batch Submissions: If you are a teacher grading a class, you can drop a ZIP file containing 50 assignments into the app. It will cross-check every single file against every other file, outputting a ranked list of the most suspicious pairs.
