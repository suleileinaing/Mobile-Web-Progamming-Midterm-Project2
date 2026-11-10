from rest_framework import viewsets
from .models import Post
from .serializers import PostSerializer
from django.utils import timezone
from django.shortcuts import render, get_object_or_404, redirect

from django.contrib.auth.decorators import login_required
from .forms import PostForm
from rest_framework.permissions import IsAuthenticatedOrReadOnly
from .permissions import IsAuthorOrReadOnly

from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from django.core.exceptions import PermissionDenied
from zoneinfo import ZoneInfo

from django.db.models.functions import TruncDate
from django.utils.dateparse import parse_date
class BlogImages(viewsets.ModelViewSet):
    queryset = Post.objects.all()
    serializer_class = PostSerializer
    
    permission_classes = [
        IsAuthenticatedOrReadOnly,
        IsAuthorOrReadOnly,
    ]

    def perform_create(self, serializer):
        serializer.save(
            author=self.request.user,
            published_date=timezone.now(),
        )

def post_list(request):
    posts = Post.objects.filter(
        published_date__isnull=False
    ).order_by('-published_date', '-id')
    
    start_value = request.GET.get("start", "")
    end_value = request.GET.get("end", "")
    date_error = ""

    try:
        start = parse_date(start_value) if start_value else None
        end = parse_date(end_value) if end_value else None

        if (start_value and start is None) or (end_value and end is None):
            raise ValueError

        if start and end and start > end:
            date_error = "시작일은 종료일보다 늦을 수 없습니다."
    except ValueError:
        date_error = "올바른 날짜를 입력해 주세요."

    if date_error:
        posts = posts.none()
    elif start or end:
        posts = posts.annotate(
            published_day=TruncDate("published_date", tzinfo=ZoneInfo("Asia/Seoul"))
        )
        if start:
            posts = posts.filter(published_day__gte=start)
        if end:
            posts = posts.filter(published_day__lte=end)

    return render(
        request,
        'blog/post_list.html',
        {
            "posts": posts,
            "start_date": start_value,
            "end_date": end_value,
            "date_error": date_error,
        },
    )
    
def post_detail(request, pk):
    post = get_object_or_404(
        Post,
        pk=pk,
        published_date__isnull=False,
    )

    return render(
        request,
        'blog/post_detail.html',
        {'post': post},
    )
    
@login_required(login_url='/api-auth/login/')
def post_new(request):
    if request.method == 'POST':
        form = PostForm(request.POST, request.FILES)

        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.published_date = timezone.now()
            post.save()

            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm()

    return render(
        request,
        'blog/post_form.html',
        {'form': form},
    )
    
@api_view(['GET'])
@permission_classes([IsAuthenticated])
def current_user(request):
    return Response({
        'id': request.user.pk,
        'username': request.user.username,
    })
    
@login_required(login_url='/api-auth/login/')
def post_edit(request, pk):
    post = get_object_or_404(Post, pk=pk)

    if post.author_id != request.user.pk:
        raise PermissionDenied

    if request.method == 'POST':
        form = PostForm(
            request.POST,
            request.FILES,
            instance=post,
        )

        if form.is_valid():
            form.save()
            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm(instance=post)

    return render(
        request,
        'blog/post_form.html',
        {
            'form': form,
            'is_edit': True,
        },
    )