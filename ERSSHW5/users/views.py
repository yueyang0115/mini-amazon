from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.shortcuts import render, redirect

from .forms import UserRegisterForm


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
    user = request.user
    if request.method == 'POST':
        opera = request.POST["operation"]
        if opera == "update_profile":
            username = request.POST["username"]
            email = request.POST["email"]
            user.username = username
            user.email = email
            user.save()
        elif opera == "update_password":
            # TODO:
            old_p = request.POST["old_password"]
            new_p = request.POST["new_password"]
            if user.check_password(old_p):
                user.set_password(new_p)
            print("do something")
        elif opera == "update_seller":
            c = request.POST.getlist("register_seller")
            if len(c) == 0:
                user.profile.is_seller = False
            else:
                user.profile.is_seller = True
            user.profile.save()

    context = {
        "username": user.username,
        "email": user.email
    }
    return render(request, 'users/profile.html', context)
