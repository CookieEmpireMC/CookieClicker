"""Layout constants of the Mill place (houses/mill.yml), shared by house_mill.py and the preview/lint scripts.

Design cells: x west -> east (0..45), y up (ground layers y -4..-1, y 0 = first air layer), z north -> south (0..35).
The road side (the yard, the viewpoint) is NORTH (-Z). Spec: design/countryside.md "Mill".
"""
SX, SY, SZ, GROUND = 46, 20, 36, 4
SIZE = (SX, SY, SZ)

ROAD_Z = (0, 2)                         # 3-wide gravel road along the north edge (the countryside's road meets it)
YARD = ((11, 3), (21, 9))               # inclusive rectangle (x, z) at ground level: 11 x 7
HALL_X = (15, 33)                       # upper floor x range
HALL_ZU = (14, 24)                      # upper floor (jetty row z 14)
HALL_ZG = (15, 24)                      # ground storey (north plane z 15)
SLOT_C = 28                             # centre column of the wheel slot (interior 25..31)
WHEEL = (28, 0, 16)                     # axle centre cell, axis z
WING_X = (8, 14)
WING_Z = (15, 23)
GRANARY_X = (2, 8)
GRANARY_Z = (4, 10)
Y_EAVE = 8
Y_EAVE_WING = 4

POINTS = {
    "viewpoint": {"at": [16, 0, 5], "yaw": -20, "pitch": 12},
    "yard": {"from": [YARD[0][0], YARD[0][1]], "to": [YARD[1][0], YARD[1][1]]},
    "hologram": [16, 1, 7],
    "panel": {"at": [20, 0, 7], "yaw": 180},
    "ripe": [12, 0, 7],
    "wheel": {"at": [WHEEL[0], WHEEL[1], WHEEL[2]], "axis": "z"},
}
