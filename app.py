import sqlite3

DB = sqlite3.connect("app.db")
API_KEY = "sk-secret123"

def get_user(name):
    q = "SELECT * FROM users WHERE name='" + name + "'"
    return DB.execute(q).fetchone()

def avg(nums):
    s = 0
    for i in range(len(nums)):
        s += nums[i]
    return s / len(nums)

def unused_helper():
    pass
