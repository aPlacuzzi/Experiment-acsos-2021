import numpy as np
import xarray as xr
import re
from pathlib import Path
import collections

def distance(val, ref):
    return abs(ref - val)
vectDistance = np.vectorize(distance)

def cmap_xmap(function, cmap):
    """ Applies function, on the indices of colormap cmap. Beware, function
    should map the [0, 1] segment to itself, or you are in for surprises.
    See also cmap_xmap.
    """
    cdict = cmap._segmentdata
    function_to_map = lambda x : (function(x[0]), x[1], x[2])
    for key in ('red','green','blue'):
        cdict[key] = map(function_to_map, cdict[key])
#        cdict[key].sort()
#        assert (cdict[key][0]<0 or cdict[key][-1]>1), "Resulting indices extend out of the [0, 1] segment."
    return matplotlib.colors.LinearSegmentedColormap('colormap',cdict,1024)

def getClosest(sortedMatrix, column, val):
    while len(sortedMatrix) > 3:
        half = int(len(sortedMatrix) / 2)
        sortedMatrix = sortedMatrix[-half - 1:] if sortedMatrix[half, column] < val else sortedMatrix[: half + 1]
    if len(sortedMatrix) == 1:
        result = sortedMatrix[0].copy()
        result[column] = val
        return result
    else:
        safecopy = sortedMatrix.copy()
        safecopy[:, column] = vectDistance(safecopy[:, column], val)
        minidx = np.argmin(safecopy[:, column])
        safecopy = safecopy[minidx, :].A1
        safecopy[column] = val
        return safecopy

def convert(column, samples, matrix):
    return np.matrix([getClosest(matrix, column, t) for t in samples])

def valueOrEmptySet(k, d):
    return (d[k] if isinstance(d[k], set) else {d[k]}) if k in d else set()

def mergeDicts(d1, d2):
    """
    Creates a new dictionary whose keys are the union of the keys of two
    dictionaries, and whose values are the union of values.
    Parameters
    ----------
    d1: dict
        dictionary whose values are sets
    d2: dict
        dictionary whose values are sets
    Returns
    -------
    dict
        A dict whose keys are the union of the keys of two dictionaries,
    and whose values are the union of values
    """
    res = {}
    for k in d1.keys() | d2.keys():
        res[k] = valueOrEmptySet(k, d1) | valueOrEmptySet(k, d2)
    return res

def extractCoordinates(filename):
    """
    Scans the header of an Alchemist file in search of the variables.
    Parameters
    ----------
    filename : str
        path to the target file
    mergewith : dict
        a dictionary whose dimensions will be merged with the returned one
    Returns
    -------
    dict
        A dictionary whose keys are strings (coordinate name) and values are
        lists (set of variable values)
    """
    with open(filename, 'r') as file:
#        regex = re.compile(' (?P<varName>[a-zA-Z._-]+) = (?P<varValue>[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?),?')
        regex = r"(?P<varName>[a-zA-Z._-]+) = (?P<varValue>[^,]*),?"
        dataBegin = r"\d"
        is_float = r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?"
        for line in file:
            match = re.findall(regex, line)
            if match:
                return {
                    var : float(value) if re.match(is_float, value)
                        else bool(re.match(r".*?true.*?", value.lower())) if re.match(r".*?(true|false).*?", value.lower())
                        else value
                    for var, value in match
                }
            elif re.match(dataBegin, line[0]):
                return {}

def extractVariableNames(filename):
    """
    Gets the variable names from the Alchemist data files header.
    Parameters
    ----------
    filename : str
        path to the target file
    Returns
    -------
    list of list
        A matrix with the values of the csv file
    """
    with open(filename, 'r') as file:
        dataBegin = re.compile('\d')
        lastHeaderLine = ''
        for line in file:
            if dataBegin.match(line[0]):
                break
            else:
                lastHeaderLine = line
        if lastHeaderLine:
            regex = re.compile(' (?P<varName>\S+)')
            return regex.findall(lastHeaderLine)
        return []

def openCsv(path):
    """
    Converts an Alchemist export file into a list of lists representing the matrix of values.
    Parameters
    ----------
    path : str
        path to the target file
    Returns
    -------
    list of list
        A matrix with the values of the csv file
    """
    regex = re.compile('\d')
    with open(path, 'r') as file:
        lines = filter(lambda x: regex.match(x[0]), file.readlines())
        return [[float(x) for x in line.split()] for line in lines]

def beautifyValue(v):
    """
    Converts an object to a better version for printing, in particular:
        - if the object converts to float, then its float value is used
        - if the object can be rounded to int, then the int value is preferred
    Parameters
    ----------
    v : object
        the object to try to beautify
    Returns
    -------
    object or float or int
        the beautified value
    """
    try:
        v = float(v)
        if v.is_integer():
            return int(v)
        return v
    except:
        if type(v) == np.str_:
            v = v.replace('\n', '').replace(' ', '_')
        return v
    
def beautifyFigName(figname):
    for symbol in r".[]\/@:":
        figname = figname.replace(symbol, '_')
    return figname

if __name__ == '__main__':
    # CONFIGURE SCRIPT
    # Where to find Alchemist data files
    directory = 'data'
    # Where to save charts
    output_directory = 'charts'
    # How to name the summary of the processed data
    pickleOutput = 'data_summary'
    # Experiment prefixes: one per experiment (root of the file name)
    experiments = ['caseStudyTd1000']
    floatPrecision = '{: 0.2f}'
    # Number of time samples 
    timeSamples = 1001
    # time management
    minTime = 0.0
    maxTime = 1000.0
    timeColumnName = 'time'
    logarithmicTime = False
    # One or more variables are considered random and "flattened"
    seedVars = ['random']
    # Label mapping
    class Measure:
        def __init__(self, description, unit = None):
            self.__description = description
            self.__unit = unit
        def description(self):
            return self.__description
        def unit(self):
            return '' if self.__unit is None else f'({self.__unit})'
        def derivative(self, new_description = None, new_unit = None):
            def cleanMathMode(s):
                return s[1:-1] if s[0] == '$' and s[-1] == '$' else s
            def deriveString(s):
                return r'$d ' + cleanMathMode(s) + r'/{dt}$'
            def deriveUnit(s):
                return f'${cleanMathMode(s)}' + '/{s}$' if s else None
            result = Measure(
                new_description if new_description else deriveString(self.__description),
                new_unit if new_unit else deriveUnit(self.__unit),
            )
            return result
        def __str__(self):
            return f'{self.description()} {self.unit()}'
    
    centrality_label = 'H_a(x)'
    def expected(x):
        return r'\mathbf{E}[' + x + ']'
    def stdev_of(x):
        return r'\sigma{}[' + x + ']'
    def mse(x):
        return 'MSE[' + x + ']'
    def cardinality(x):
        return r'\|' + x + r'\|'

    labels = {
        'time': Measure('time', 's'),
        'nodes[Max]': Measure('node count'),
        'S1Distance[Max]': Measure('distance from destination', 'm'),
        'S2Distance[Max]': Measure('distance from destination', 'm'),
        'S3Distance[Max]': Measure('distance from destination', 'm'),
        'S1ChannelDistance[Max]': Measure('gradient distance from destination', 'm'),
        'S2ChannelDistance[Max]': Measure('gradient distance from destination', 'm'),
        'S3ChannelDistance[Max]': Measure('gradient distance from destination', 'm'),
        'S1DistanceTraveled[Max]': Measure('distance traveled from source', 'm'),
        'S2DistanceTraveled[Max]': Measure('distance traveled from source', 'm'),
        'S3DistanceTraveled[Max]': Measure('distance traveled from source', 'm'),
        'rounds[Sum]': Measure('execution round count'),
    }
    def derivativeOrMeasure(variable_name):
        if variable_name.endswith('dt'):
            return labels.get(variable_name[:-2], Measure(variable_name)).derivative()
        return Measure(variable_name)
    def label_for(variable_name):
        return labels.get(variable_name, derivativeOrMeasure(variable_name)).description()
    def unit_for(variable_name):
        return str(labels.get(variable_name, derivativeOrMeasure(variable_name)))
    
    # Setup libraries
    np.set_printoptions(formatter={'float': floatPrecision.format})
    # Read the last time the data was processed, reprocess only if new data exists, otherwise just load
    import pickle
    import os
    separator = os.path.sep
    if os.path.exists(directory):
        newestFileTime = max([os.path.getmtime(directory + separator + file) for file in os.listdir(directory)], default=0.0)
        try:
            lastTimeProcessed = pickle.load(open('timeprocessed', 'rb'))
        except:
            lastTimeProcessed = -1
        shouldRecompute = not os.path.exists(".skip_data_process") and newestFileTime != lastTimeProcessed
        if not shouldRecompute:
            try:
                means = pickle.load(open(pickleOutput + '_mean', 'rb'))
            except: 
                shouldRecompute = True
        if shouldRecompute:
            timefun = np.logspace if logarithmicTime else np.linspace
            means = {}
            for experiment in experiments:
                # Collect all files for the experiment of interest
                import fnmatch #  + '_*.txt'
                allfiles = filter(lambda file: fnmatch.fnmatch(file, '20210509-' + experiment + '_*'), os.listdir(directory))
                allfiles = [directory + separator + name for name in allfiles]
                allfiles.sort()
                # From the file name, extract the independent variables
                dimensions = {}
                for file in allfiles:
                    dimensions = mergeDicts(dimensions, extractCoordinates(file))
                dimensions = {k: sorted(v) for k, v in dimensions.items()}
                # Add time to the independent variables
                dimensions[timeColumnName] = range(0, timeSamples)
                # Compute the matrix shape
                shape = tuple(len(v) for k, v in dimensions.items())
                # Prepare the Dataset
                dataset = xr.Dataset()
                for k, v in dimensions.items():
                    dataset.coords[k] = v
                if len(allfiles) == 0:
                    print("WARNING: No data for experiment " + experiment)
                    means[experiment] = dataset
                else:
                    varNames = extractVariableNames(allfiles[0])
                    for v in varNames:
                        if v != timeColumnName:
                            novals = np.ndarray(shape)
                            novals.fill(float('nan'))
                            dataset[v] = (dimensions.keys(), novals)
                    # Compute maximum and minimum time, create the resample
                    timeColumn = varNames.index(timeColumnName)
                    allData = { file: np.matrix(openCsv(file)) for file in allfiles }
                    computeMin = minTime is None
                    computeMax = maxTime is None
                    if computeMax:
                        maxTime = float('-inf')
                        for data in allData.values():
                            maxTime = max(maxTime, data[-1, timeColumn])
                    if computeMin:
                        minTime = float('inf')
                        for data in allData.values():
                            minTime = min(minTime, data[0, timeColumn])
                    timeline = timefun(minTime, maxTime, timeSamples)
                    # Resample
                    for file in allData:
    #                    print(file)
                        allData[file] = convert(timeColumn, timeline, allData[file])
                    # Populate the dataset
                    for file, data in allData.items():
                        dataset[timeColumnName] = timeline
                        for idx, v in enumerate(varNames):
                            if v != timeColumnName:
                                darray = dataset[v]
                                experimentVars = extractCoordinates(file)
                                darray.loc[experimentVars] = data[:, idx].A1
                    # Fold the dataset along the seed variables, producing the mean and stdev datasets
                    mergingVariables = [seed for seed in seedVars if seed in dataset.coords]
                    means[experiment] = dataset.mean(dim = mergingVariables, skipna=True)
            # Save the datasets
            pickle.dump(means, open(pickleOutput + '_mean', 'wb'), protocol=-1)
            #☻pickle.dump(newestFileTime, open('timeprocessed', 'wb'))
    else:
        means = { experiment: xr.Dataset() for experiment in experiments }

    # QUICK CHARTING

    import matplotlib
    from matplotlib import rc
    import matplotlib.pyplot as plt
    import matplotlib.cm as cmx
    from mpl_toolkits.mplot3d import Axes3D # needed for 3d projection
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection
    
    matplotlib.rcParams.update({'axes.titlesize': 12})
    matplotlib.rcParams.update({'axes.labelsize': 10})
#    Custom charting
#    colors = ['#33a02c','#e31a1c','#1f78b4', '#000000FF']
    colors_length = 3
    colors = [cmx.viridis(float(i)/(colors_length - 1)) for i in range(colors_length)]

    def make_line_chart(xdata, ydata1, ydata1Label, ydata1Color, verticalLinePos, xlabel = '', ylabel = '', title = '', filename = ''):
        fig = plt.figure(figsize=(6,4))
        ax = fig.add_subplot(1, 1, 1)
        ax.set_title(title)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.set_xlim([0, max(xdata)])
        ax.set_ylim([-50, max(ydata1) + 500])
        ax.plot(xdata[:ydata1.size], ydata1, label=ydata1Label, color=ydata1Color, linewidth=1.0)
        ax.axvline(verticalLinePos, color='#878787', linestyle='dashed', linewidth=1.0)
        ax.legend()
        plt.tight_layout()
        fig.savefig(filename)
        plt.close(fig)
        
    def make_double_line_chart(xdata, ydata1, ysize1, ydata1Label, ydata1Color, ydata2, ysize2, ydata2Label, ydata2Color, verticalLinePos, yLim = [], xlabel = '', ylabel = '', title = '', filename = ''):
        fig = plt.figure(figsize=(6,4))
        ax = fig.add_subplot(1, 1, 1)
        ax.set_title(title)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.set_xlim([0, max(xdata)])
        if yLim == []:
            yLim = [-50,max(max(ydata1), max(ydata2)) + 500]
        ax.set_ylim(yLim)
        ax.plot(xdata[-ysize1:], ydata1, label=ydata1Label, color=ydata1Color, linewidth=1.0)
        ax.plot(xdata[-ysize2:], ydata2, label=ydata2Label, color=ydata2Color, linewidth=1.0)
        for x in verticalLinePos:
            ax.axvline(x, color='#878787', linestyle='dashed', linewidth=1.0)
        ax.legend()
        plt.tight_layout()
        fig.savefig(filename)
        plt.close(fig)

    def generate_round_chart(data, title, baseDir, columnName = 'rounds[Sum]', startFromIndex = 1):
        time = data[timeColumnName].values[startFromIndex:]
        withGrid = []
        previousR = 0
        for r in data.sel(gridStep = 0.0055)[columnName].values[startFromIndex:]:
            rInt = int(r)
            withGrid.append(rInt - previousR)
            previousR = rInt
        withoutGrid = []
        previousR = 0
        for r in data.sel(gridStep = 1.0055)[columnName].values[startFromIndex:]:
            rInt = int(r)
            withoutGrid.append(rInt - previousR)
            previousR = rInt
        chartFileName = f'{baseDir}{separator}{columnName}-delta.pdf'
        yLim = [min(min(withGrid), min(withoutGrid)) - 100, max(max(withGrid), max(withoutGrid)) + 100]
        make_double_line_chart(time, withGrid, len(withGrid), 'with virtual nodes', colors[1], withoutGrid, len(withoutGrid), 'without virtual nodes', colors[2], [], yLim = yLim, xlabel = labels[timeColumnName], ylabel = labels[columnName], title = title, filename = chartFileName)
     
    def generate_round_chart1(data, title, baseDir, columnName = 'rounds[Sum]', startFromIndex = 1):
        time = data[timeColumnName].values[startFromIndex:]
        withGrid = data.sel(gridStep = 0.0055)[columnName].values[startFromIndex:] / time
        withoutGrid = data.sel(gridStep = 1.0055)[columnName].values[startFromIndex:] / time
        chartFileName = f'{baseDir}{separator}{columnName}.pdf'
        yLim = [min(min(withGrid), min(withoutGrid)) - 100, max(max(withGrid), max(withoutGrid)) + 100]
        make_double_line_chart(time, withGrid, withGrid.size, 'with virtual nodes', colors[1], withoutGrid, withoutGrid.size, 'without virtual nodes', colors[2], [], yLim = yLim, xlabel = labels[timeColumnName], ylabel = labels[columnName], title = title, filename = chartFileName)
           
    def generate_chart_by_column_name(data, columnName, startTime, title, baseDir, startFromIndex = 1):
        time = data[timeColumnName].values[startFromIndex:]
        withGrid = interpolate(data.sel(gridStep = 0.0055)[columnName].values[startFromIndex:])
        withoutGrid = interpolate(data.sel(gridStep = 1.0055)[columnName].values[startFromIndex:])
        chartFileName = f'{baseDir}{separator}{columnName}.pdf'
        if (withoutGrid.size > 0):
            make_double_line_chart(time, withGrid, withGrid.size, 'with virtual nodes', colors[1], withoutGrid, withoutGrid.size, 'without virtual nodes', colors[2], [startTime], xlabel = labels[timeColumnName], ylabel = labels[columnName], title = title, filename = chartFileName)
        else:
            make_line_chart(time, withGrid, 'with virtual nodes', colors[1], startTime, xlabel = labels[timeColumnName], ylabel = labels[columnName], title = title, filename = chartFileName)    
    
    import pandas as pd
    def interpolate(data):
        notInf = []
        findFirstNotInf = False
        for v in data:
            if (findFirstNotInf and (v == float("inf"))):
                notInf.append(np.nan)
            else:
                if (v != float("inf")):
                    notInf.append(v)
                    findFirstNotInf = True
        return pd.Series(notInf).interpolate()
    
    def generate_charts(means, errors = None, basedir=''):
        Path(basedir).mkdir(parents=True, exist_ok=True)
        startTimes = [10, 40, 100]
        for i in range(3):
            nodeToExport = i + 1
            generate_chart_by_column_name(means, f'S{nodeToExport}Distance[Max]', startTimes[i], f'Source {nodeToExport}', basedir)
            generate_chart_by_column_name(means, f'S{nodeToExport}ChannelDistance[Max]', startTimes[i], f'Source {nodeToExport}', basedir, startTimes[i] + 1)
            generate_chart_by_column_name(means, f'S{nodeToExport}DistanceTraveled[Max]', startTimes[i], f'Source {nodeToExport}', basedir)
        generate_round_chart(means, 'mean execution round count', basedir)
        generate_round_chart1(means, 'delta execution round count', basedir)

    for experiment in experiments:
        current_experiment_means = means[experiment]
        generate_charts(current_experiment_means, basedir=output_directory)
        