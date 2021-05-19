'''
Remember to remove the sep= from the top of the csv file, manually
'''

import csv
import json

csvpaths=[r'log/tournament-20210507-163302-EnergySmall-B-domain.log.csv',
r'log/tournament-20210507-150731-NiceOrDie-A-domain.log.csv',
r'log/tournament-20210507-163450-FlightBooking-C-domain.log.csv',
r'log/tournament-20210507-163249-Phone-A-domain.log.csv' ]

all_utils_path = 'stats/all_utils.json'

data = {}
names = {}
all_utils = {}

for n, csvFilePath in enumerate(csvpaths):
    data = {}
    names = {}

    with open(csvFilePath, encoding='utf-8') as csvf:
        csvReader = csv.DictReader(csvf, delimiter=';')

        for i, rows in enumerate(csvReader):
            key = i
            data[key] = rows

            # Make agent names unique
            data[i]['Agent 1'] = data[i]['Agent 1'].split('@', 1)[0]
            data[i]['Agent 2'] = data[i]['Agent 2'].split('@', 1)[0]

            # Construct name dictionary and get components
            if data[i]['Agent 1'] not in names:
                if len(data[i]['Agent 1']) > 100:
                    names[data[i]['Agent 1']] = "boa_"+str(i)
                else:
                    names[data[i]['Agent 1']] = data[i]['Agent 1']

    # Change names
    for key in data:
        data[key]['Agent 1'] = names[data[key]['Agent 1']]
        data[key]['Agent 2'] = names[data[key]['Agent 2']]


    for key in data:
        if (data[key]['Exception'] != ""):
            continue

        agent1 = data[key]['Agent 1']
        agent1_util = float(data[key]['Utility 1'])

        agent2 = data[key]['Agent 2']
        agent2_util = float(data[key]['Utility 1'])

        if agent1 not in all_utils:
            all_utils[agent1] = [agent1_util]
        else:
            all_utils[agent1].append(agent1_util)

        if agent2 not in all_utils:
            all_utils[agent2] = [agent2_util]
        else:
            all_utils[agent2].append(agent2_util)


with open(all_utils_path, 'w', encoding='utf-8') as jsonf:
    jsonf.write(json.dumps(all_utils, indent=2))
