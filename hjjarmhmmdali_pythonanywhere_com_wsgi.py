import sys
import os

project_path = '/home/hjjarmhmmdali/mysite'
if project_path not in sys.path:
    sys.path.insert(0, project_path)

py_path = os.path.join(project_path, 'py')
if py_path not in sys.path:
    sys.path.insert(0, py_path)

os.environ['PYTHONANYWHERE_SITE'] = 'true'

from flask_app_home import application