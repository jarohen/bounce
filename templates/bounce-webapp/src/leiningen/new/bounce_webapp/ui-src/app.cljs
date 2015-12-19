(ns {{name}}.ui.app
  (:require [clojure.string :as s]
            [reagent.core :as r]))

(enable-console-print!)

(defn code [s]
  [:strong {:style {:font-family "'Courier New', 'monospace'"}}
   s])

(defn page-component []
  [:div.container {:style {:margin-top "1em"}}
   "Hello world!"]

  ;; ------------------------------------------------------------

  ;; Below this line is only required for the Bounce welcome page, feel
  ;; free to just delete all of it when you want to get cracking on your
  ;; own project!

  [:div.container
   [:h2 {:style {:margin-top "1em"}}
    "Hello from Bounce!"]

   [:h3 "Things to try:"]

   [:ul
    [:li [:p "In your Clojure REPL, run " [code "(bounce.core/reload!)"] " to completely reload the webapp without restarting the JVM."]]
    [:li [:p "Start making your webapp!"]
     [:ul
      [:li [:p
            "The CLJS entry point is in " [code "ui-src/{{sanitized}}/ui/app.cljs"] ". "
            "Figwheel should auto-reload any connected browsers on every save."]]
      [:li [:p "The Clojure system entry point is in " [code "src/{{sanitized}}/service/main.clj"]]]
      [:li [:p "The Clojure Ring handler is in " [code "src/{{sanitized}}/service/handler.clj"]]]]]

    [:li [:p "Connect to the CLJS browser REPL"]
     [:ol
      [:li "Connect to the normal server-side REPL (port 7888, by default)"]
      [:li "Evaluate: " [code "(bounce.figwheel/cljs-repl (bounce.core/ask :cljs-compiler)"]]
      [:li "Refresh this page"]
      [:li "When you get a " [code "cljs.user =>"] " prompt, you can test it with:"
       [:ul
        [:li [code "(+ 1 1)"]]
        [:li [code "(js/window.alert \"Hello world!\")"]]
        [:li [code "(set! (.-backgroundColor js/document.body.style) \"green\")"]]]]]]

    [:li [:p "Any trouble, let me know - either through GitHub or on Twitter at " [:a {:href "https://twitter.com/jarohen"} "@jarohen"]]]

    [:li [:p "Good luck!"]]]

   [:div {:style {:text-align "right"
                  :font-weight "bold"}}
    [:p
     [:span {:style {:font-size "1.3em"}} "James Henderson"]
     [:br]
     "Twitter: " [:a {:href "https://twitter.com/jarohen"} "@jarohen"]
     [:br]
     "GitHub: " [:a {:href "https://github.com/jarohen"} "jarohen"]]]])

(defn render-page! []
  (r/render-component [page-component] js/document.body))

(set! (.-onload js/window)
      (fn []
        (render-page!)))
