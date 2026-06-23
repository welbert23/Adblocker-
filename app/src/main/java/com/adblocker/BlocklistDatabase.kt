package com.adblocker

object BlocklistDatabase {
    val AD_DOMAINS: Set<String> = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "adnxs.com", "appnexus.com",
        "rubiconproject.com", "openx.net",
        "pubmatic.com", "casalemedia.com",
        "criteo.com", "criteo.net",
        "adsrvr.org", "advertising.com",
        "adtech.com", "adtechus.com",
        "amazon-adsystem.com", "aax.amazon-adsystem.com",
        "plugrush.com", "popads.net", "popadscdn.net",
        "trafficjunky.com"
    )

    val ADULT_DOMAINS: Set<String> = setOf(
        // --- Major tube sites ---
        "pornhub.com", "xvideos.com", "xnxx.com",
        "xhamster.com", "xhamsterlive.com",
        "redtube.com", "youporn.com",
        "tube8.com", "spankwire.com", "keezmovies.com",
        "extremetube.com", "porntube.com", "sunporno.com",
        "eporner.com", "hdsex.org", "beeg.com",
        "tnaflix.com", "empflix.com", "watchmygf.com",
        "porntrex.com", "pornhd.com", "porn300.com",
        "porndoe.com", "pornhubpremium.com",
        "perfectgirls.net",
        "hclips.com", "hiperdex.com",
        "heavy-r.com", "analvids.com",
        "pornoxo.com", "desixnxx.com",
        "xxxbunker.com", "pornwhite.com",
        "porn7x.com", "porncor.com",
        "pornfay.com", "pornhat.com",
        "pornicom.com", "pornid.com",
        "pornmaki.com", "pornmega.com",
        "xvideos.red", "xvideoz.com",
        "xhamster.desi", "xhamster2.com",

        // --- Cam sites ---
        "chaturbate.com", "livejasmin.com",
        "myfreecams.com", "stripchat.com", "cam4.com",
        "cams.com", "bongacams.com", "streamate.com",
        "camster.com", "flingster.com",
        "camdolls.com", "camsoda.com",
        "imlive.com", "royalcams.com",
        "xlovecam.com", "webcams.com",
        "adultcammaster.com",

        // --- Premium / Studios ---
        "onlyfans.com", "fansly.com", "manyvids.com",
        "bangbros.com", "brazzers.com", "realitykings.com",
        "vixen.com", "blacked.com", "deeper.com",
        "teamskeet.com", "mofos.com",
        "naughtyamerica.com", "wicked.com",
        "evilangel.com", "digitalplayground.com",
        "elegantangel.com", "dorcelclub.com",
        "pornfidelity.com", "twistys.com",
        "girlsway.com", "puretaboo.com",
        "vivid.com", "hustler.com",
        "penthouse.com", "playboy.com",
        "clips4sale.com", "iwantclips.com",

        // --- Dating / hookup ---
        "sex.com", "adultfriendfinder.com",
        "flirt.com", "ashleymadison.com",
        "fuckbook.com", "friendfinder.com",
        "swinglifestyle.com", "fetlife.com",

        // --- Erotica / Stories ---
        "literotica.com", "nifty.org",
        "storiesonline.net", "sexstories.com",
        "erotica.com", "luscious.net",

        // --- Hentai / Doujin / Anime porn ---
        "nhentai.net", "hentaihaven.org",
        "hentaigasm.com", "hentaifox.com",
        "pururin.io", "simply-hentai.com",
        "hentai2read.com", "hitomi.la",
        "e-hentai.org", "exhentai.org",
        "t-nhentai.com", "hanime.tv",
        "hentaistream.com", "hentai.tv",

        // --- Image boards / galleries ---
        "gelbooru.com", "rule34.xxx",
        "danbooru.donmai.us", "safebooru.org",
        "xbooru.com",
        "tbib.org", "realbooru.com",
        "pixiv.net", "paheal.net",

        // --- Amateur / User content ---
        "motherless.com", "erome.com",
        "imgchili.net", "imagefap.com",
        "imgbabes.com", "pimpandhost.com",
        "imgbox.com", "imagebam.com",
        "pixhost.to", "imgtaxi.com",
        "imagevenue.com", "freeimagehosting.net",
        "scrolller.com", "nsfwalbum.com",

        // --- Random chat / video chat ---
        "omegle.com", "chatrandom.com", "shagle.com",
        "coomeet.com", "chatroulette.com",
        "ome.tv", "tinychat.com",
        "strangercam.com", "minichat.com",
        "bazoocam.com", "faceflow.com",

        // --- Porn search engines ---
        "sleazyneighbor.com", "theporndude.com",
        "pornstar.com", "porngo.com",
        "pornmd.com", "sexsearch.com",
        "pornve.com", "porndish.com",

        // --- Nude celebs / leaks ---
        "celebjihad.com", "leakedmodels.com",
        "fappeningbook.com", "thefappening.plus",
        "thothub.com", "celebsroulette.com",
        "nudostar.com", "scandalplanet.com",
        "fappenist.com", "ww7.godigital.com",

        // --- Japanese / Asian adult ---
        "javdb.com", "javlibrary.com",
        "avgle.com", "javmost.com",
        "javhd.com", "javmix.com",
        "r18.com", "japanesebeauties.com",
        "jav.guru", "javdoe.com",
        "missav.com", "jp.netcdn.space",
        "shemalejapan.com",

        // --- Gay / LGBT adult ---
        "gaymaletube.com", "gayporn.com",
        "gaytube.com", "gaymenring.com",
        "gayboystube.com", "gaypornmasters.com",
        "men.com", "belami.com",
        "gayheaven.org", "gayporno.fm",
        "gaydemon.com", "twinkporn.com",
        "boyfriendtv.com",

        // --- Trans / Shemale ---
        "shemale.com", "shemaleporntube.com",
        "shemalez.com", "tsporntube.com",
        "tgtube.com", "shemalevideos.com",
        "shemalesex.com", "tsladyboy.com",

        // --- Live TV / adult streaming ---
        "juicyads.com", "exoclick.com",
        "ero-advertising.com", "adultadworld.com",
        "trafficfactory.biz", "trafficjunky.com",
        "mgid.com", "adsterra.com",
        "propellerads.com", "popunder.net",
        "adultdatelink.com", "adultmoda.com",
        "adultadvertising.net",

        // --- Misc adult / NSFW communities ---
        "nsfw.xxx", "adultempire.com",
        "adultdvdempire.com",
        "fuskator.com", "bellesa.co",
        "nsfwmonster.com",
        "nsfwcrave.com", "nsfwhub.com"
    )

    val SAFE_DOMAINS: Set<String> = setOf(
        "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "google.co",
        "youtube.com", "ytimg.com", "googlevideo.com", "ggpht.com",
        "facebook.com", "facebook.net", "fb.com", "fbsbx.com", "fbcdn.net",
        "messenger.com",
        "instagram.com", "cdninstagram.com", "instagramstatic.com",
        "tiktok.com", "tiktokcdn.com", "tiktokv.com", "byteoversea.com",
        "pstatp.com", "snssdk.com", "musical.ly",
        "reddit.com", "redditmedia.com", "redd.it",
        "wikipedia.org", "wikimedia.org",
        "amazon.com", "amazonaws.com", "cloudfront.net",
        "netflix.com", "nflxvideo.net",
        "spotify.com", "spotifycdn.com",
        "microsoft.com", "live.com", "msn.com",
        "apple.com", "icloud.com",
        "github.com", "githubusercontent.com",
        "stackoverflow.com",
        "zoom.us",
        "discord.com", "discordapp.com",
        "linkedin.com",
        "telegram.org",
        "bing.com", "duckduckgo.com",
        "medium.com",
        "slack.com", "trello.com",
        "office.com", "office365.com",
        "cloudflare.com", "cloudflare.net",
        "cdnjs.com", "jsdelivr.net", "unpkg.com",
        "pinterest.com", "tumblr.com",
        "snapchat.com",
        "aliexpress.com", "shopify.com",
        "paypal.com", "stripe.com",
        "wordpress.com", "blogger.com",
        "npmjs.com", "python.org",
        "adobe.com",
        "vimeo.com", "dailymotion.com",
        "twitch.tv", "twitter.com", "twimg.com", "x.com",
        "roblox.com", "epicgames.com",
        "steampowered.com", "nintendo.com",
        "playstation.com", "xbox.com",
        "tesla.com", "spacex.com",
        "dropbox.com", "doodle.com", "drupal.org",
        "whatsapp.com", "whatsapp.net",
        "disney.com", "disneyplus.com",
        "hulu.com", "hbomax.com", "peacock.com",
        "paramount.com",
        "oracle.com", "ibm.com", "salesforce.com",
        "yahoo.com", "xerox.com", "zoho.com",
        "uber.com", "lyft.com",
        "nytimes.com", "wsj.com", "cnn.com", "bbc.com", "bbc.co.uk",
        "espn.com", "foxnews.com", "nbcnews.com",
        "imgur.com", "gfycat.com", "tenor.com",
        "wtfismyip.com", "ipify.org",
        "brightcove.com",
        "bugsnag.com", "sentry.io", "firebaseio.com",
        "googletagmanager.com", "google-analytics.com"
    )
}
