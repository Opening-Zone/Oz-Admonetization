Oz Ads - business layer:

The business layer contain 2 type of ads :
    -inline ads and overlay ads :
        +inline ads are banner, native ads, which involve in the app layout
        +overlay ads are ads that take overlay on app , independence with the app layout

--OzAdsManager.kt : for init the ads network, and init config for the bussiness layer. This class represent for the business layer.
--OzAds.kt : abstract layer for ALL type of ads, include common method to do the business :
    + abstract : loadAds, showAds. The class doesn't care about how the ads is get from network and show, so the methods will be abstract. 
Class just perform the logic to : 
        - Load ads and save to the map
        - ads state control
        - destroy ads 

--InlineAds.kt : the implementation of OzAds. It provide more spercific business logic around the inline ads : mainly focus on refreshing ads
--OverlayAds.kt : the implementation of OzAds. It provide more spercific business logic around the overlay ads : mainly focus on showing ads (create overlay, adding view)

-OzAdmobBannerAd: the banner ads implementation of InlineAds. It will implement the abtract load/ show method with the method from network/admobs/ads_component
-OzAdmobNativeAd: the native ads implementation of InlineAds. It will implement the abtract load/ show method with the method from network/admobs/ads_component

--network/admobs/ads_component : contain class to perform request ads from admob (load) and show that ads (show) these code use at the end of 
hireachy