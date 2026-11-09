from django.contrib.auth import get_user_model
from rest_framework import status
from rest_framework.test import APITestCase

from .models import Post

import tempfile
from io import BytesIO

from PIL import Image
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings

from django.utils import timezone

from rest_framework.authtoken.models import Token

class PostAPITests(APITestCase):
    def test_get_empty_post_list(self):
        response = self.client.get('/api_root/Post/')

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )
        self.assertEqual(response.json(), [])

    def test_get_existing_post(self):
        user = get_user_model().objects.create_user(
            username='testuser',
            password='test-password',
        )

        Post.objects.create(
            author=user,
            title='Test photo',
            text='My test post',
            image='blog_image/test.jpg',
        )

        response = self.client.get('/api_root/Post/')
        data = response.json()

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]['title'], 'Test photo')
        self.assertEqual(data[0]['text'], 'My test post')
        self.assertEqual(data[0]['author'], user.pk)
        self.assertTrue(
            data[0]['image'].endswith('/media/blog_image/test.jpg')
        )
        
    def test_post_without_required_fields_is_rejected(self):
        user = get_user_model().objects.create_user(
            username='validationuser',
            password='test-password',
        )
        self.client.force_authenticate(user=user)

        response = self.client.post(
            '/api_root/Post/',
            {},
            format='multipart',
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST,
        )
        self.assertIn('title', response.data)
        self.assertIn('image', response.data)
        self.assertEqual(Post.objects.count(), 0)
        response = self.client.post(
            '/api_root/Post/',
            {},
            format='multipart',
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST,
        )
        self.assertIn('title', response.data)
        self.assertIn('image', response.data)
        self.assertEqual(Post.objects.count(), 0)
        
    def test_create_post_with_image(self):
        user = get_user_model().objects.create_user(
            username='uploader',
            password='test-password',
        )
        self.client.force_authenticate(user=user)

        buffer = BytesIO()
        Image.new('RGB', (10, 10), color='blue').save(
            buffer,
            format='PNG',
        )
        image = SimpleUploadedFile(
            'test.png',
            buffer.getvalue(),
            content_type='image/png',
        )

        with tempfile.TemporaryDirectory() as media_dir:
            with override_settings(MEDIA_ROOT=media_dir):
                response = self.client.post(
                    '/api_root/Post/',
                    {
                        'author': user.pk,
                        'title': 'Upload test',
                        'text': 'Uploaded through the API',
                        'image': image,
                    },
                    format='multipart',
                )

                self.assertEqual(
                    response.status_code,
                    status.HTTP_201_CREATED,
                )
                self.assertEqual(Post.objects.count(), 1)

                post = Post.objects.get()
                self.assertEqual(post.title, 'Upload test')
                self.assertEqual(post.author, user)
                self.assertTrue(
                    post.image.storage.exists(post.image.name)
                )
    def test_publish_post_and_display_title(self):
        user = get_user_model().objects.create_user(
            username='publisher',
            password='test-password',
        )
        post = Post.objects.create(
            author=user,
            title='Published photo',
            text='Publishing test',
            image='blog_image/test.jpg',
        )

        self.assertIsNone(post.published_date)

        post.publish()
        post.refresh_from_db()

        self.assertIsNotNone(post.published_date)
        self.assertEqual(str(post), 'Published photo')
        
    def test_homepage_shows_only_published_posts(self):
        user = get_user_model().objects.create_user(
            username='blogreader',
            password='test-password',
        )

        published = Post.objects.create(
            author=user,
            title='Published photo',
            text='Visible description',
            image='blog_image/test.jpg',
            published_date=timezone.now(),
        )

        Post.objects.create(
            author=user,
            title='Draft photo',
            text='Hidden description',
            image='blog_image/draft.jpg',
            published_date=None,
        )

        response = self.client.get('/')

        self.assertEqual(response.status_code, 200)
        self.assertTemplateUsed(response, 'blog/post_list.html')
        self.assertEqual(
            list(response.context['posts']),
            [published],
        )
        self.assertContains(response, 'Published photo')
        self.assertContains(response, 'Visible description')
        self.assertNotContains(response, 'Draft photo')
        
    def test_author_can_delete_own_post(self):
        author = get_user_model().objects.create_user(
            username='delete_author',
            password='test-password',
        )

        post = Post.objects.create(
            author=author,
            title='My photo',
            text='Delete test',
            image='blog_image/test.jpg',
        )

        self.client.force_authenticate(user=author)

        response = self.client.delete(
            f'/api_root/Post/{post.pk}/'
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )
        self.assertFalse(
            Post.objects.filter(pk=post.pk).exists()
        )

    def test_other_user_cannot_delete_post(self):
        author = get_user_model().objects.create_user(
            username='original_author',
            password='test-password',
        )
        other_user = get_user_model().objects.create_user(
            username='other_user',
            password='test-password',
        )

        post = Post.objects.create(
            author=author,
            title='Protected photo',
            text='Another user cannot delete this',
            image='blog_image/test.jpg',
        )

        self.client.force_authenticate(user=other_user)

        response = self.client.delete(
            f'/api_root/Post/{post.pk}/'
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )
        self.assertTrue(
            Post.objects.filter(pk=post.pk).exists()
        )
        
    def test_detail_only_shows_published_post(self):
        user = get_user_model().objects.create_user(
            username='detailreader',
            password='test-password',
        )

        published = Post.objects.create(
            author=user,
            title='Published detail',
            text='Photo description',
            image='blog_image/test.jpg',
            published_date=timezone.now(),
        )

        draft = Post.objects.create(
            author=user,
            title='Private draft',
            text='Not published yet',
            image='blog_image/draft.jpg',
            published_date=None,
        )

        response = self.client.get(
            f'/post/{published.pk}/'
        )

        self.assertEqual(response.status_code, 200)
        self.assertTemplateUsed(
            response,
            'blog/post_detail.html',
        )
        self.assertContains(response, 'Published detail')
        self.assertContains(response, 'Photo description')

        draft_response = self.client.get(
            f'/post/{draft.pk}/'
        )

        self.assertEqual(draft_response.status_code, 404)
        
    def test_logged_in_user_can_open_upload_form(self):
        user = get_user_model().objects.create_user(
            username='formreader',
            password='test-password',
        )
        self.client.force_login(user)

        response = self.client.get('/post/new/')

        self.assertEqual(response.status_code, 200)
        self.assertTemplateUsed(
            response,
            'blog/post_form.html',
        )
        self.assertIn('form', response.context)

    def test_web_upload_rejects_missing_fields(self):
        user = get_user_model().objects.create_user(
            username='formvalidator',
            password='test-password',
        )
        self.client.force_login(user)

        response = self.client.post('/post/new/', {})

        self.assertEqual(response.status_code, 200)
        self.assertTemplateUsed(
            response,
            'blog/post_form.html',
        )

        errors = response.context['form'].errors

        self.assertIn('title', errors)
        self.assertIn('text', errors)
        self.assertIn('image', errors)
        self.assertEqual(Post.objects.count(), 0)
        
    def test_web_upload_creates_published_post(self):
        user = get_user_model().objects.create_user(
            username='webuploader',
            password='test-password',
        )
        self.client.force_login(user)

        buffer = BytesIO()
        Image.new('RGB', (10, 10), color='blue').save(
            buffer,
            format='PNG',
        )

        image = SimpleUploadedFile(
            'web-test.png',
            buffer.getvalue(),
            content_type='image/png',
        )

        with tempfile.TemporaryDirectory() as media_dir:
            with override_settings(MEDIA_ROOT=media_dir):
                response = self.client.post(
                    '/post/new/',
                    {
                        'title': 'Web upload test',
                        'text': 'Uploaded through the website',
                        'image': image,
                    },
                    format='multipart',
                )

                self.assertEqual(Post.objects.count(), 1)

                post = Post.objects.get()

                self.assertEqual(post.author, user)
                self.assertEqual(post.title, 'Web upload test')
                self.assertEqual(
                    post.text,
                    'Uploaded through the website',
                )
                self.assertIsNotNone(post.published_date)
                self.assertTrue(
                    post.image.storage.exists(post.image.name)
                )
                self.assertRedirects(
                    response,
                    f'/post/{post.pk}/',
                )
    def test_anonymous_user_can_read_post_detail(self):
        author = get_user_model().objects.create_user(
            username='publicauthor',
            password='test-password',
        )

        post = Post.objects.create(
            author=author,
            title='Public photo',
            text='Anyone can view this',
            image='blog_image/test.jpg',
            published_date=timezone.now(),
        )

        response = self.client.get(
            f'/api_root/Post/{post.pk}/'
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )
        self.assertEqual(response.data['id'], post.pk)
        self.assertEqual(
            response.data['title'],
            'Public photo',
        )
        
    def test_current_user_returns_token_owner(self):
        user = get_user_model().objects.create_user(
            username='tokenowner',
            password='test-password',
        )
        token, _ = Token.objects.get_or_create(user=user)

        self.client.credentials(
            HTTP_AUTHORIZATION=f'Token {token.key}'
        )

        response = self.client.get('/api_root/me/')

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )
        self.assertEqual(response.data['id'], user.pk)
        self.assertEqual(
            response.data['username'],
            'tokenowner',
        )

    def test_current_user_rejects_anonymous_request(self):
        response = self.client.get('/api_root/me/')

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )
        
    def test_author_can_edit_own_post(self):
        author = get_user_model().objects.create_user(
            username='edit_author',
            password='test-password',
        )

        post = Post.objects.create(
            author=author,
            title='Original title',
            text='Original description',
            image='blog_image/original.jpg',
            published_date=timezone.now(),
        )

        original_image = post.image.name
        original_published_date = post.published_date

        self.client.force_login(author)

        form_response = self.client.get(
            f'/post/{post.pk}/edit/'
        )

        self.assertEqual(form_response.status_code, 200)
        self.assertTemplateUsed(
            form_response,
            'blog/post_form.html',
        )

        response = self.client.post(
            f'/post/{post.pk}/edit/',
            {
                'title': 'Updated title',
                'text': 'Updated description',
            },
        )

        post.refresh_from_db()

        self.assertRedirects(
            response,
            f'/post/{post.pk}/',
        )
        self.assertEqual(post.title, 'Updated title')
        self.assertEqual(post.text, 'Updated description')
        self.assertEqual(post.author, author)
        self.assertEqual(post.image.name, original_image)
        self.assertEqual(
            post.published_date,
            original_published_date,
        )
        self.assertEqual(Post.objects.count(), 1)
        
    def test_other_user_cannot_edit_post(self):
        author = get_user_model().objects.create_user(
            username='edit_owner',
            password='test-password',
        )
        other_user = get_user_model().objects.create_user(
            username='edit_other',
            password='test-password',
        )

        post = Post.objects.create(
            author=author,
            title='Protected title',
            text='Protected description',
            image='blog_image/test.jpg',
            published_date=timezone.now(),
        )

        self.client.force_login(other_user)

        url = f'/post/{post.pk}/edit/'

        form_response = self.client.get(url)
        self.assertEqual(form_response.status_code, 403)

        response = self.client.post(
            url,
            {
                'title': 'Unauthorized change',
                'text': 'Should not be saved',
            },
        )

        self.assertEqual(response.status_code, 403)

        post.refresh_from_db()

        self.assertEqual(post.title, 'Protected title')
        self.assertEqual(post.text, 'Protected description')
        self.assertEqual(post.author, author)