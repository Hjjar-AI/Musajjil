# run.py
import sys
import os
import socket
from py.app import app
from py.config import PORT
from py.database import init_db, get_db
from py.database_seed import seed_data

if __name__ == '__main__':
    # Force HTTP
    os.environ['AUTHLIB_INSECURE_TRANSPORT'] = '1'
    
    init_db()
    with app.app_context():
        db = get_db()
        if db.execute('SELECT COUNT(*) FROM classifications').fetchone()[0] == 0:
            seed_data()
            print("Database seeded.")
        else:
            print("Database already has data.")
    
    hostname = socket.gethostname()
    local_ip = socket.gethostbyname(hostname)
    
    print(f"\nStarting HTTP server on http://0.0.0.0:{PORT}")
    print(f"Local access: http://127.0.0.1:{PORT}")
    print(f"Network access: http://{local_ip}:{PORT}")
    print("Press Ctrl+C to stop.\n")
    
    app.run(host='0.0.0.0', port=PORT, debug=False, threaded=True)