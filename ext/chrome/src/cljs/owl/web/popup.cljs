(ns owl.web.popup
  (:use [domina :only [by-id
                       value
                       set-value!
                       set-attr!
                       set-text!
                       set-style!]])
  (:require [domina.events :as ev]))

(defonce ^:export proxy-settings (atom {:value {:mode ""
                                                :rules {}
                                                :pacScript {}}
                                        :scope "regular"}))
(defonce ^:export proxy-switch (atom false))
(defonce ^:export proxy-types {:raw [:auto_detect
                                     :pac_script
                                     :direct
                                     :fixed_servers
                                     :system]
                               :run ["pac_script"
                                     "fixed_servers"]})
(declare on-proxy-error!)

(defn set-link! [id uri]
  (set-attr! (by-id id) :href (.getURL js/chrome.runtime uri)))

(defn make-proxy-settings! [uri]
  (when-let [u (re-find #"(\w+)://([\w\.]+)(:(\d+))?(/(\w+\.\w+))?" uri)]
    (let [p {:url (first u)
             :scheme (nth u 1)
             :host (nth u 2)
             :port (nth u 4)
             :has-wpad? (not (nil? (nth u 6)))}
          s (if (:has-wpad? p)
              (assoc-in @proxy-settings [:value]
                        {:mode "pac_script"
                         :pacScript {:url (:url p)
                                     :mandatory true}})
              (assoc-in @proxy-settings [:value]
                        {:mode "fixed_servers"
                         :rules {:singleProxy
                                 {:scheme (:scheme p)
                                  :host (:host p)
                                  :port (js/parseInt (:port p))}}}))]
      (reset! proxy-settings s))))

(defn proxy-settings-to-url [s]
  (when-let [p (:value s)]
    (let [m (:mode p)]
      (case m
        "pac_script" (:url (:pacScript p))
        "fixed_servers" (let [u (:singleProxy (:rules p))]
                         (str (:scheme u)
                              (:host u)
                              (:port u)))
        nil))))

(defn switch-proxy! []
  (let [g (clj->js {:incognito false})]
    (.get js/chrome.proxy.settings
          g (fn [d]
              (let [c (js->clj d :keywordize-keys true)
                    m (:mode (:value c))
                    b (by-id "proxy_run")
                    u (by-id "proxy_uri")]
                (if (some #(= m %) (:run proxy-types))
                  (do
                    (reset! proxy-switch true)
                    (set-value! u (proxy-settings-to-url c))
                    (set-style! u :background-color "RoyalBlue")
                    (set-style! u :color "White")
                    (set-value! b "Stop"))
                  (do
                    (reset! proxy-switch false)
                    (set-value! u (proxy-settings-to-url @proxy-settings))
                    (set-style! u :background-color "")
                    (set-style! u :color "")
                    (set-value! b "Run "))))))))

(defn apply-proxy-settings! [e]
  (let [c (make-proxy-settings! (value (by-id "proxy_uri")))
        d (clj->js c)]
    (.preventDefault e.evt)
    (.stopPropagation e.evt)
    (.log js/console d)
    ;;(.. js/chrome -proxy -onProxyError (addListener on-proxy-error!))
    (.set js/chrome.proxy.settings
          d (fn [s]
              (.log js/console (clj->js s))))
    (.sendRequest js/chrome.extension {:type "clearError"})))

(defn clear-proxy-settings! []
  (let [d (clj->js {:scope "regular"})]
    (.clear js/chrome.proxy.settings d
            (fn [] (.log js/console "#clear-proxy-settings")))))

(defn on-proxy-run! [e]
  (let [s @proxy-switch]
    (if (true? s)
      (do
        (clear-proxy-settings!))
      (do
        (apply-proxy-settings! e)))
    (switch-proxy!)))

(defn on-proxy-error! [e]
  (let [d (js->clj e :keywordize-keys true)]
    (.log js/console (:fatal d))
    (.log js/console (:error d))
    (.log js/console (:details d))))

(defn on-doc-ready []
  (when-let [ready-state (.-readyState js/document)]
    (if (and (= "complete" ready-state)
             (by-id "popup"))
      (do 
          (.log js/console "#popup:on-doc-ready")
          (set-link! "options_link" "resources/public/options.html")
          (set-link! "echo_link" "resources/public/echo.html")
          (switch-proxy!)
          (ev/listen! (by-id "proxy_run") :click on-proxy-run!)
        true)
      false)))

(set! (.-onreadystatechange js/document) on-doc-ready)
