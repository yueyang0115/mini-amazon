from django.db import models
from django.contrib.auth.models import User


class Profile(models.Model):
    user = models.OneToOneField(User, primary_key=True, on_delete=models.CASCADE)
    isSeller = models.BooleanField(default=False)

    def __str__(self):
        return f'{self.user.username} Profile'
