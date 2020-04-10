from django.db import models

# Create your models here.


# This is the class which represent one specific item
class Item(models.Model):
    description = models.CharField(max_length=100, blank=False)


class Product(models.Model):
    item = models.ForeignKey(Item, on_delete=models.CASCADE, related_name="item")
    cnt = models.IntegerField()

    def __str__(self):
        return


class Package(models.Model):
    def __str__(self):
        return ""
