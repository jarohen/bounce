(ns {{name}}.ui.app
  (:require [bounce.core :as bc]
            [bounce.mux :as mux]
            [bounce.mux.bidi :as mux.bidi]
            [reagent.core :as r]))

(enable-console-print!)

(defn page-view []
  [:div.container {:style {:margin-top "1em"}}
   "Hello world!"]

  ;; ------------------------------------------------------------

  ;; Below this line is only required for the Bounce welcome page, feel
  ;; free to just delete all of it when you want to get cracking on your
  ;; own project!

  (letfn [(code [s]
            [:strong {:style {:font-family "'Courier New', 'monospace'"}}
             s])]
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
        [:li "When you get a " [code "cljs.user =>"] " prompt, you can test it with:"
         [:ul
          [:li [code "(+ 1 1)"]]
          [:li [code "(js/window.alert \"Hello world!\")"]]
          [:li [code "(set! (.-backgroundColor js/document.body.style) \"green\")"]]
          [:li [code "(bounce.core/snapshot)"] " - to get the current system state"]
          [:li [code "(bounce.core/reload!)"] " - to reset the system state"]]]]]

      [:li [:p "Any trouble, let me know - either through GitHub or on Twitter at " [:a {:href "https://twitter.com/jarohen"} "@jarohen"]]]

      [:li [:p "Good luck!"]]]

     [:div {:style {:text-align "right"
                    :font-weight "bold"}}
      [:p
       [:span {:style {:font-size "1.3em"}} "James Henderson"]
       [:br]
       "Twitter: " [:a {:href "https://twitter.com/jarohen"} "@jarohen"]
       [:br]
       "GitHub: " [:a {:href "https://github.com/jarohen"} "jarohen"]]]]))

(defn render-page! []
  (r/render-component [(fn []
                         [@(r/cursor (bc/ask :!app) [::root-component])])]
                      js/document.body))

(defn make-bounce-map []
  {:!app (fn []
           (bc/->component (r/atom {})))

   :router (-> (fn []
                 (mux/make-router {:token-mapper (mux.bidi/token-mapper ["" {"/" ::main-page}])
                                   :listener (fn [{:keys [location page]}]
                                               (reset! (r/cursor (bc/ask :!app) [:location]) location)
                                               (reset! (r/cursor (bc/ask :!app) [::root-component]) page))

                                   :default-location {:handler ::main-page}

                                   :pages {::main-page (fn [{:keys [old-location new-location same-handler?]}]
                                                         (when-not same-handler?
                                                           ;; mount!
                                                           )

                                                         (mux/->page (fn []
                                                                       [page-view])

                                                                     (fn [{:keys [old-location new-location same-handler?]}]
                                                                       (when-not same-handler?
                                                                         ;; un-mount!
                                                                         ))))}}))
               (bc/using #{:!app}))

   :renderer (-> (fn []
                   (bc/->component (render-page!)
                                   (fn []
                                     (r/unmount-component-at-node js/document.body))))

                 (bc/using #{:!app :router}))})

(set! (.-onload js/window)
      (fn []
        (bc/set-system-map-fn! make-bounce-map)
        (bc/start!)))
