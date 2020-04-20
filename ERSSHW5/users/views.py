from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.contrib.auth.models import User
from django.contrib.auth.password_validation import validate_password, password_validators_help_text_html
from django.core.exceptions import ValidationError
from django.shortcuts import render, redirect
from django.urls import reverse

from .forms import UserRegisterForm


def register(request):
    if request.method == 'POST':
        form = UserRegisterForm(request.POST)
        if form.is_valid():
            form.save()
            return redirect('login')
    else:
        form = UserRegisterForm()
    return render(request, 'users/register.html', {'form': form})


@login_required
def profile(request):
    context = {}
    user = request.user
    context["username"] = user.username
    context["email"] = user.email
    if request.method == 'POST':
        opera = request.POST["operation"]
        if opera == "update_profile":
            username = request.POST["username"]
            email = request.POST["email"]
            context["username"] = username
            context["email"] = email
            errors = check_username(username, user)
            if len(errors) == 0:
                user.username = username
                user.email = email
                user.save()
            else:
                context["name_errors"] = errors
        elif opera == "update_password":
            old_p = request.POST["old_password"]
            new_p = request.POST["new_password"]
            errors = check_password(old_p=old_p, new_p=new_p, user=user)
            if len(errors) == 0:
                user.set_password(new_p)
                user.save()
                messages.success(request, "successfully change the password")
                return redirect(reverse("login"))
            else:
                context["password_errors"] = errors
        elif opera == "update_seller":
            c = request.POST.getlist("register_seller")
            if len(c) == 0:
                user.profile.is_seller = False
            else:
                user.profile.is_seller = True
            user.profile.save()

    context["help_text"] = password_validators_help_text_html
    return render(request, 'users/profile.html', context)


def check_username(username, user):
    if " " in username:
        return ["username contains space"]
    if username != user.username:
        try:
            User.objects.get(username=username)
            return ["this name has already been used"]
        except User.DoesNotExist:
            return []
    return []


def check_password(old_p, new_p, user):
    # check old password
    if not user.check_password(old_p):
        return ["old password not match"]
    # check whether is the same
    if old_p == new_p:
        return ["old and new password are the same"]
    # check new password
    try:
        validate_password(new_p)
    except ValidationError as e:
        errors = []
        for error in e.error_list:
            errors.extend(error.messages)
        return errors
    return []
