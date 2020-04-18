from django.apps import AppConfig
from django.db.models.signals import post_migrate


def migrate_callback(sender, **kwargs):
    # Your specific logic here
    print("after migrate")
    from amazon.models import Item
    if Item.objects.all().count() == 0:
        # we should insert some new data here
        print("insert new data")
    else:
        print("already has data")


class AmazonConfig(AppConfig):
    name = 'amazon'

    def ready(self):
        post_migrate.connect(migrate_callback, sender=self)
