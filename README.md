Due to the repository size, pushing this project directly to the remote repository encountered issues. However, you can still work on this by cloning the following repository and replacing the relevant python files with those in this repo, all of which have the same names:

Clone this repository:

bash
Copy code
https://github.com/Noobcoder64/interpolation-repair
Clone and follow all the instructions in the ReadME. Replace the relevant python scripts and add the two jar files in this repo to your Spectra directory. The file labelled no order does not reorder the initial states during trace generation, the other does. 

Update your LD_LIBRARY_PATH to point to the Spectra library as follows:
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/path/to/spectra
