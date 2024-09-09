Due to the repository size, pushing this project directly to the remote repository encountered issues on the day of the project deadline. In order to work-around this, I suggest that you clone the existing ISPEC refinement reposity and replace the relevant python files with those in this repo, all of which have the same names (apart from counterstrategy which is now counterstrategypython due to a naming conflict):

Clone this repository:

https://github.com/Noobcoder64/interpolation-repair
Clone and follow all the instructions in the ReadME. Replace the relevant python scripts and add the two jar files in this repo to your Spectra directory. The file labelled no order does not reorder the initial states during trace generation, the other does. They can be used interchangably in spectra_utils.py: 

jpype.startJVM(classpath=["/homes/pmb223/interpolation-repair/spectra/SpectraToolTraceCycleMemExpMinVarOutput**NoOrder**.jar", 
               "/homes/pmb223/interpolation-repair/spectra/SpectraTool.jar", 
               "/homes/pmb223/interpolation-repair/spectra/dependencies/*"])

The java files in this repo are a small part of the compiled java code used in this project, their only relevance is to show how traces are computed. 

Update your LD_LIBRARY_PATH to point to the Spectra library as follows:
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/path/to/spectra
