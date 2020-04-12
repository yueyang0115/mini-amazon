from django.shortcuts import render
from .models import Package, Item, Product

# Create your views here.

def home(request):
    return render(request, "amazon/home.html")

def new_order(request):
    return render(request, 'amazon/new_order.html')

def buy(request):
    if request.method == 'POST':
        apple_cnt = request.POST['apple_cnt']
        orange_cnt = request.POST['orange_cnt']
        dest_x = request.POST['x']
        dest_y = request.POST['y']

        if apple_cnt != 0 or orange_cnt != 0:
            newPackage = Package(dest_x=dest_x, dest_y=dest_y)
            newPackage.save()

            if (apple_cnt != 0):
                # item = Item.objects.filter(description="apple").first()
                # if not item:
                item = Item(description="apple")
                item.save()
                newProduct = Product(item=item, cnt=apple_cnt, package=newPackage)
                newProduct.save()

            if (orange_cnt != 0):
                # item = Item.objects.filter(description="orange").first()
                # if not item:
                item = Item(description="orange")
                item.save()
                newProduct = Product(item=item, cnt=orange_cnt, package=newPackage)
                newProduct.save()

    return render(request, 'amazon/success.html')
