(ns {{name}}.service.handler
  (:require [{{name}}.service.css :as css]
            [bidi.bidi :as bidi]
            [bidi.ring :as br]
            [bounce.core :as bc]
            [bounce.figwheel :as cljs]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.util.response :refer [response content-type]]))

;; This is all in one NS for now, but you'll likely want to split it
;; out when your webapp grows!

(def site-routes
  ["" {"/" {:get :page-handler}
       "/css" {"/site.css" {:get :site-css}}
       "/webjars" (br/resources {:prefix "META-INF/resources/webjars"})}])

(defn page-handler [req]
  (-> (response
       (html5
        [:head
         [:title "{{name}} - CLJS Single Page Web Application"]

         (include-js "/webjars/jquery/2.1.4/jquery.min.js")
         (include-js "/webjars/bootstrap/3.3.5/js/bootstrap.min.js")
         (include-css "/webjars/bootstrap/3.3.5/css/bootstrap.min.css")

         (include-js (cljs/path-for-js (bc/ask :cljs-compiler)))
         (include-css (bidi/path-for site-routes :site-css :request-method :get))]

        [:body]))

      (content-type "text/html")))

(defn site-handlers []
  {:page-handler page-handler
   :site-css (fn [req]
               (-> (response (css/site-css))
                   (content-type "text/css")))})

(def api-routes
  ["/api" {}])

(defn api-handlers []
  {})

(defn make-handler []
  (br/make-handler ["" [site-routes
                        api-routes
                        (cljs/bidi-routes (bc/ask :cljs-compiler))]]

                   (some-fn (site-handlers)
                            (api-handlers)

                            #(when (fn? %) %)

                            (constantly {:status 404
                                         :body "Not found."}))))
