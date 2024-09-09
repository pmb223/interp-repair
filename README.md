Interpolation Repair
Due to the repository size, pushing this project directly to the remote repository encountered issues. However, you can still work on this by cloning the following repository and replacing the modified Python files, all of which have the same names:

Clone this repository:
https://github.com/Noobcoder64/interpolation-repair

Setup Instructions
1. Install Conda
Refer to the Official Conda Installation Guide
2. Create Conda Environments
Bash
conda create -n py38 python=3.8
conda create -n py27 python=2.7
Use code with caution.

3. Activate Python 3.8 Environment

conda activate py38

4. Install Required Packages
Package	Version	Installation Command
Python Spot	v2.11.6	conda install -c conda-forge spot
Pyparsing	v3.1.1	conda install -c conda-forge pyparsing
Numpy	v1.25.2	conda install -c conda-forge numpy
Pandas	latest	conda install pandas
Matplotlib	latest	conda install matplotlib

5. JAR Files
Make sure to place your JAR files in the appropriate directory as needed by the project.
If required, add any missing JAR files in your local repository setup to ensure proper functionality.
6. Spectra
Update your LD_LIBRARY_PATH to point to the Spectra library:
Bash
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/path/to/spectra

Add the jar files in this repository to the spectra directory. The one labelled No Order does not re-order the 
