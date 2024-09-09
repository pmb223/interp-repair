Interpolation Repair Repository
Due to the repository size, pushing this project directly to the remote repository encountered issues. However, you can still work on this by cloning the following repository and modifying the Python files, all of which have the same names:

Clone this repository:

bash
Copy code
https://github.com/Noobcoder64/interpolation-repair
Once cloned, make sure to follow the steps below for setting up the necessary environment and adding the JAR files as per the project requirements.

Setup Instructions
Step	Command
Install Conda	Official Conda Installation Guide
Create Conda Environments	conda create -n py38 python=3.8
conda create -n py27 python=2.7
Activate Python 3.8 Environment	conda activate py38
Install Python Spot (v2.11.6)	Spot Installation Guide
conda install -c conda-forge spot
Install Pyparsing (v3.1.1)	Pyparsing on Conda Forge
conda install -c conda-forge pyparsing
Install Numpy (v1.25.2)	Official Numpy Installation Guide
conda install -c conda-forge numpy
Install Pandas	Pandas Installation Guide
conda install pandas
Install Matplotlib	Matplotlib Installation Guide
conda install matplotlib
JAR Files
Make sure to place your JAR files in the appropriate directory as needed by the project. If required, add any missing JAR files in your local repository setup to ensure proper functionality.

Spectra
Update your LD_LIBRARY_PATH to point to the Spectra library as follows:

bash
Copy code
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/path/to/spectra
