// fallback-loader.js - load local resources when CDN fails
(function() {
    function loadFallback(url, type) {
        if (type === 'css') {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = url;
            document.head.appendChild(link);
        } else if (type === 'js') {
            var script = document.createElement('script');
            script.src = url;
            script.defer = true;
            document.head.appendChild(script);
        }
    }

    // Only attempt fallback if the resource actually failed to load
    // using onerror is safe and does not violate connect-src.
    var bootstrapCss = document.querySelector('link[href*="bootstrap.rtl.min.css"]');
    if (bootstrapCss) {
        bootstrapCss.onerror = function() {
            console.warn('Bootstrap CSS CDN failed, loading local fallback');
            loadFallback('/static/css/bootstrap.rtl.min.css', 'css');
        };
    }

    var bootstrapJs = document.querySelector('script[src*="bootstrap.bundle.min.js"]');
    if (bootstrapJs) {
        bootstrapJs.onerror = function() {
            console.warn('Bootstrap JS CDN failed, loading local fallback');
            loadFallback('/static/js/bootstrap.bundle.min.js', 'js');
        };
    }
})();