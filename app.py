def divide(a, b):
    return a / b  # BUG: no ZeroDivisionError handling


def find_user(users, name):
    for u in users:
        if u["name"] == name:
            return u
    return None


def total(items):
    s = 0
    for i in range(len(items)):
        s += items[i]
    return s
