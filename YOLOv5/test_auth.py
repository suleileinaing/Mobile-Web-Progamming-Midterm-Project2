import getpass
import requests

username = input("Django username: ")
password = getpass.getpass("Django password: ")

response = requests.post(
    "http://127.0.0.1:8000/api-token-auth/",
    data={
        "username": username,
        "password": password,
    },
    timeout=10,
)

print("HTTP:", response.status_code)
response.raise_for_status()

token = response.json().get("token")
print("Token received:", bool(token))