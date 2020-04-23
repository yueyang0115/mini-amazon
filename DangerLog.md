4.10:
    When a new purchase request created at the front-end, it should notify back-end.  
    So we created a socket for communications between front-end and back-end. Front-end will first store the package into database and then notify the daemon by sending the id.  

4.11:
    Make the communication process asynchronous.   
    We created a thread for handling request from Django front-end while the main thread is used to communicate with UPS and the world.  
    For asynchronous functions like toPack and toPurchase, thread-pool is used and functions will return immediately.  

4.12: 
    Users that are not login shouldn't get access to purchasing, viewing orders and other web pages.  
    So we added "@login_required" for related functions in views.py and fixed other model-related bugs.  

4.13:
    Basic process realized with mocked UPS, mocked-UPS only support two trucks for now, can only buy two packages at the same time.  

4.14:
    Fixed multi-thread problem related to sequence number, use synchronized methods.  

4.15-4.22:
    More front-end web pages and functionalities are added, user experience improved.   

4.17:
    Fixed synchronize-related problems.  

4.18:
    Fixed bugs in shopping-cart web page.  

4.19: 	
    The database has empty tables when first created, no items will be listed on the webpage.  
    So we created default users, categories, items and warehouse into database at first migration.  

4.22:
    Fixed bugs in showing the total price of an order and in modifying a selling product.  
