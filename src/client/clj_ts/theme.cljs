(ns clj-ts.theme)

(defn viewing? [db-or-mode]
  (let [value @db-or-mode]
    (or (= :viewing value)
        (= :viewing (:mode value)))))

(defn light-theme? [db-or-theme]
  (or (= :light db-or-theme)
      (when (satisfies? IDeref db-or-theme)
        (or
          (= :light @db-or-theme)
          (= :light (:theme @db-or-theme))))))

(defn set-light-theme! [db]
  (swap! db assoc :theme :light))

(defn set-dark-theme! [db]
  (swap! db assoc :theme :dark))

;; region DOM theming

(defn get-initial-theme [fallback-theme]
  (let [persisted-theme (js/localStorage.getItem "theme")]
    (if persisted-theme
      (keyword persisted-theme)
      fallback-theme)))

(defn- get-theme-host-element []
  (.-documentElement js/document))

(defn- get-link-element [title]
  (js/document.querySelector (str "link[title=\"" title "\"]")))

(defn- get-link-element-dark []
  (get-link-element "dark"))

(defn- get-link-element-light []
  (get-link-element "light"))

(def dark-mode-class-name "theme-dark")

(defn- toggle-highlightjs-stylesheets [to-be-enabled-stylesheet to-be-disabled-stylesheet]
  (.setAttribute to-be-disabled-stylesheet "disabled" "disabled")
  (.removeAttribute to-be-enabled-stylesheet "disabled"))

(defn- apply-dark-mode []
  (js/localStorage.setItem "theme" "dark")
  (let [theme-host (get-theme-host-element)]
    (.add (.-classList theme-host) dark-mode-class-name))
  (let [light-mode-stylesheet (get-link-element-light)
        dark-mode-stylesheet (get-link-element-dark)]
    (toggle-highlightjs-stylesheets dark-mode-stylesheet light-mode-stylesheet)))

(defn- apply-light-mode []
  (js/localStorage.setItem "theme" "light")
  (let [body-element (get-theme-host-element)]
    (.remove (.-classList body-element) dark-mode-class-name))
  (let [light-mode-stylesheet (get-link-element-light)
        dark-mode-stylesheet (get-link-element-dark)]
    (toggle-highlightjs-stylesheets light-mode-stylesheet dark-mode-stylesheet)))

(defn toggle-app-theme [db]
  (let [theme (:theme @db)]
    (if (light-theme? theme)
      (apply-light-mode)
      (apply-dark-mode))))

;; endregion
