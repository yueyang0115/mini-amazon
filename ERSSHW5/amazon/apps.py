from django.apps import AppConfig
from django.db.models.signals import post_migrate


def default_users():
    # check and creat a default user if not exist
    from django.contrib.auth.models import User
    try:
        User.objects.get(username="mini amazon")
    except User.DoesNotExist:
        print("")
        mini_amazon = User.objects.create(
            username="mini amazon",
            email="miniamazon@noreply.com",
            is_superuser=False
        )
        mini_amazon.set_password("amazon12345")
        mini_amazon.save()


def default_items():
    print("default items")
    from amazon.models import Item
    if Item.objects.all().count() == 0:
        # TODO: we should insert some new data here
        print("insert new data")
    else:
        print("already has data")


def migrate_callback(sender, **kwargs):
    # Your specific logic here
    print("after migrate")
    default_users()
    default_items()


class AmazonConfig(AppConfig):
    name = 'amazon'

    def ready(self):
        post_migrate.connect(migrate_callback, sender=self)
