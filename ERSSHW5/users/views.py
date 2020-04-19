from django.shortcuts import render, redirect
from django.contrib import messages
from .forms import UserRegisterForm, UserUpdateForm, ProfileUpdateForm
from django.contrib.auth.decorators import login_required


def register(request):
    if request.method == 'POST':
        form = UserRegisterForm(request.POST)
        if form.is_valid():
            form.save()
            username = form.cleaned_data.get('username')
            messages.success(request, f'You account have already created. You can login now!')
            return redirect('login')
    else:
        form = UserRegisterForm()
    return render(request, 'users/register.html', {'form': form})


@login_required
def profile(request):
    if request.method == 'POST':
        username = request.POST["username"]
        email = request.POST["email"]
        request.user.username = username
        request.user.email = email
        request.user.save()

        return redirect('profile')

    context = {
        "username": request.user.username,
        "email": request.user.email
    }
    return render(request, 'users/profile.html', context)
