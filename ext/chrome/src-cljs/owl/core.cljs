(ns owl.web.core)

(defn say-hello []
  (js/alert "Hello, ClojureScript!"))

(defn add-some-numbers [& numbers]
  (apply + numbers))
