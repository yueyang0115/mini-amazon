# erss-hwk5-kx30-yy58

Fancy Amazon!

## Feature checklist(requirement)

- [x] Buy products(communicate with both the world and UPS).
- [x] Different catalog of products.
- [x] Check the status of an order.
- [x] Specify the deliver address(i.e. (x,y)).
- [x] Specify a UPS account name to associate the order with.
- [x] Provide the *tracking number* for the shipment.

## Extra features we have

- [x] A **full-featured shopping cart**(implemented by jQuery + Ajax).
    - check(& uncheck) any orders you want
    - can change the item count of each order dynamically
    - delete any order you don't need anymore
    - the price will change according to your action(e.g. delete order, change count)
- [x] A **full-featured product management system** + seller.
    - any authenticated user can register as a seller(or unregister)
    - sellers will have a product management system which he/she can 
        - publish new products
        - edit their selling products(e.g. name, price, category)
        - delete(or on sell) their selling products
- [x] Search bar in home page.
    - search products from all category
    - search products in one specic category
- [x] A **full-featured order page**.
    - search bar --- locate any order quickly
    - delete any history order
- [x] Build-in data
    - use **signals** to make sure have some build-in data(e.g. initial items, defualt user), easy to deploy
- [x] Warehouse **dynamic alloccation**.
    - we have 10 build-in warehouses(as part of the initial data)
    - we will allocate the nearest warehouse to each package(to facilate the delivery process)
- [x] Product category.
    - several category of products, can switch between them in the home page
- [x] Edit user information
    - a separate page to allow user edit his/her personal infromation(e.g. name, email, password)
- [ ] Associate your amazon account with your UPS account.
    - automatically associate each order with your UPS account
- [ ] Address book.
    - Store your frequently use addre into address book and fill out the address autimatically when checkout.
- [x] User-friendly UI and interaction.
    - all edit info page will have some error handling, will show the error message if failed
    - use jQuery + aJax to make interaction more smooth(e.g. use partial refresh in shopping cart)