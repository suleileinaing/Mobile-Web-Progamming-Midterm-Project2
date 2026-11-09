import getpass
import os
import requests
from datetime import datetime
import pathlib

import cv2

class ChangeDetection:
    def __init__(self, names):
        self.result_prev = [0 for _ in range(len(names))]
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
        self.title = ''
        self.text = ''
        change_flag = 0 #변화 감지 플레그
        i = 0
        while i < len(self.result_prev):
            if self.result_prev[i]==0 and detected_current[i]==1:
                change_flag = 1
                self.title = names[i]
                self.text += names[i] + ", "
            i += 1
            
        self.result_prev = detected_current[:] # 객체 검출 상태 저장
        if change_flag==1:
            self.send(save_dir, image)
            
            
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
        
        file = {'image' : open(full_path,'rb')}
        res = requests.post(self.host + '/api_root/Post/', data=data, files=file, headers=headers)
        print(res)