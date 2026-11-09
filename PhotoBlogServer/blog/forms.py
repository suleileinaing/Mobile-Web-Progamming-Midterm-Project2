from django import forms

from .models import Post


class PostForm(forms.ModelForm):
    class Meta:
        model = Post
        fields = ['title', 'text', 'image']

        labels = {
            'title': '제목',
            'text': '내용',
            'image': '사진',
        }

        widgets = {
            'title': forms.TextInput(attrs={
                'placeholder': '제목을 입력해 주세요.',
            }),
            'text': forms.Textarea(attrs={
                'placeholder': '사진에 대한 이야기를 적어 주세요.',
                'rows': 5,
            }),
        }