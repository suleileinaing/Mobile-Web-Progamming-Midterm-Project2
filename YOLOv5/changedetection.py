import getpass
import os
import requests
from datetime import datetime
import pathlib
from time import monotonic
import cv2

class ChangeDetection:
    def __init__(self, names):
        self.result_prev = [0 for _ in range(len(names))]
        self.upload_cooldown_seconds = 10
        self.last_upload_at = {}
        self.title = ""
        self.text = ""

        self.host = os.environ.get(
            "PHOTO_BLOG_HOST",
            "http://127.0.0.1:8000",
        ).rstrip("/")

        username = input("Django username: ")
        password = getpass.getpass("Django password: ")

        response = requests.post(
            self.host + "/api-token-auth/",
            data={
                "username": username,
                "password": password,
            },
            timeout=10,
        )
        response.raise_for_status()

        self.token = response.json()["token"]
        print("Django login successful.")
        
    def add(self, names, detected_current, save_dir, image):
        now = monotonic()
        appeared = []

        for i in range(len(self.result_prev)):
            if self.result_prev[i] != 0 or detected_current[i] != 1:
                continue

            name = names[i]
            last_upload = self.last_upload_at.get(name)

            if last_upload is not None:
                elapsed = now - last_upload
                if elapsed < self.upload_cooldown_seconds:
                    print(f"Skipped duplicate: {name} ({elapsed:.1f}s)")
                    continue

            appeared.append(name)

        self.result_prev = detected_current[:]

        if appeared:
            person_detected = any(
                detected_current[i] == 1 and names[i] == "person"
                for i in range(len(detected_current))
            )
            if person_detected:
                window = "Confirm person photo upload"
                preview = image.copy()
                cv2.putText(
                    preview,
                    "Y: Upload | N or Esc: Skip",
                    (20, 45),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    (0, 255, 255),
                    2,
                )
                cv2.imshow(window, preview)

                try:
                    while True:
                        key = cv2.waitKey(50) & 0xFF
                        if cv2.getWindowProperty(window, cv2.WND_PROP_VISIBLE) < 1:
                            print("Upload skipped by user.")
                            return
                        if key in (ord("y"), ord("Y")):
                            break
                        if key in (ord("n"), ord("N"), 27):
                            print("Upload skipped by user.")
                            return
                finally:
                    if cv2.getWindowProperty(window, cv2.WND_PROP_VISIBLE) >= 1:
                        cv2.destroyWindow(window)
                
            self.title = appeared[-1]
            self.text = ", ".join(appeared)

            if self.send(save_dir, image):
                uploaded_at = monotonic()
                for name in appeared:
                    self.last_upload_at[name] = uploaded_at
            
            
    def send(self, save_dir, image):
        now = datetime.now()
        now.isoformat()
        
        today = datetime.now()
        save_path = os.getcwd() / save_dir / 'detected' / str(today.year) / str(today.month) / str(today.day)
        pathlib.Path(save_path).mkdir(parents=True, exist_ok=True)
        
        full_path = save_path / '{0}-{1}-{2}-{3}.jpg'.format(today.hour,today.minute,today.second,today.microsecond)
        
        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(full_path, dst)
        # 인증이필요한요청에아래의headers를붙임
        
        headers = {'Authorization' : "Token " + self.token, 'Accept' : 'application/json'}
        # Post Create
        data = {
        'title' : self.title,
        'text' : self.text,
        'created_date' : now,
        'published_date' : now
        }
        
        with open(full_path, "rb") as image_file:
            res = requests.post(
                self.host + "/api_root/Post/",
                data=data,
                files={"image": image_file},
                headers=headers,
                timeout=30,
            )

        print(res)
        return res.status_code == 201