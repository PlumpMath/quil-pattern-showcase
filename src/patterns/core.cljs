(ns ^:figwheel-always patterns.core
  (:require-macros [reagent.ratom :refer [reaction]]
                   [secretary.core :refer [defroute]])
  (:require
   [quil.core :as q :include-macros true]
   [quil.middleware :as m]
   [reagent.core :as reagent :refer [atom]]
   [re-frame.core :refer [register-handler
                          debug
                          path
                          register-sub
                          dispatch
                          dispatch-sync
                          subscribe]]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import [goog History]))

(enable-console-print!)

;; state
;; -----------------------------------------------------------------------------

(def sketch-w 400)
(def sketch-h 400)

(def patterns
  {:base
   {:title "Base"
    :setup
    (fn base-setup []
      {:t 1})
    :update
    (fn base-update [state]
      (update-in state [:t] inc))
    :draw
    (fn base-draw [state]
      (q/background 255)
      (q/fill 0)
      (q/ellipse (rem (:t state) (q/width)) 46 55 55))
    }
   :wew
   {:title "wew lad"
    :setup
    (fn wew-setup []
      (q/color-mode :hsb)
      {:t 1 :radius 100})
    :update
    (fn wew-update [state]
      (update-in state [:t] inc))
    :draw
    (fn wew-draw [state]
      (q/background (rem (:t state) 255) 255 200)
      (q/fill 255)
      (q/with-translation [(/ (q/width) 2) (/ (q/height) 2)]
        (q/ellipse
        (* 100 (q/cos (q/radians (rem (:t state) 360))))
        (* 100 (q/sin (q/radians (rem (:t state) 360))))
        (:radius state) (:radius state))))}
   })

(def base-state {:current :base})

;; routing
;; -----------------------------------------------------------------------------

(defonce h (History.))

(defn initialize-routes []
  (secretary/set-config! :prefix "#")
  (goog.events/listen h
                      EventType/NAVIGATE
                      #(secretary/dispatch! (.-token %)))
  (doto h (.setEnabled true)))

(defroute home-path "/" []
  (dispatch [:initialize]))

(defroute pattern-path "/pattern/:pattern-name" [pattern-name]
  (dispatch [:set-pattern pattern-name]))

;; handlers
;; -----------------------------------------------------------------------------

(register-handler
 :initialize
 debug
 (fn [db [_]]
   (merge db base-state)))

(register-handler
 :set-pattern
 debug
 (fn [db [_ pattern-name]]
   (assoc db :current (keyword pattern-name))))

;; subscriptions
;; -----------------------------------------------------------------------------

(register-sub
 :current
 (fn [db _]
   (reaction (:current @db))))

;; components
;; -----------------------------------------------------------------------------


(defn quil-canvas [{:keys [setup update draw]}]
  (let [current-draw (atom nil)
        current-update (atom nil)
        new-state (atom false)]
    (reagent/create-class
     {:reagent-render
      (fn [] [:canvas#sketch
              {:width sketch-w :height sketch-h}])
      :component-did-mount
      (fn [_]
        (reset! current-draw draw)
        (reset! current-update update)
        (q/sketch
         :setup setup
         :update #(@current-update %)
         :draw #(@current-draw %)
         :host "sketch"
         :size [sketch-w sketch-h]
         :middleware [m/fun-mode]
         ))
      :component-did-update
      (fn [this]
        (let [{:keys [setup update draw]} (-> this reagent/props)]
          (reset! new-state true)
          (reset! current-update
                  (fn [state]
                    (if @new-state
                      (do (reset! new-state false)
                          (setup))
                      (update state))))
          (reset! current-draw draw)
          ))
      })))

(defn scaffold []
  (let [current (subscribe [:current])]
    (fn inner-render []
      (if @current
        ;; render sketch
        [:div
         (if-let [pattern (patterns @current)]
           [:div
            [:h1 (:title (patterns @current))]
            [quil-canvas (patterns @current)]]
           [:h1 "Not found."])
           [:ul
            (map (fn [[k v]]
                   [:li {:key (name k)}
                    [:a
                     {:href (if (= :base k)
                              (home-path)
                              (pattern-path {:pattern-name (name k)}))}
                     (:title v)]])
                 patterns)]]
        ; render loading.
        [:h1 "Loading"])
      )))

;; plumbing
;; -----------------------------------------------------------------------------

(defn application []
  (fn render-root [] [:div [scaffold]]))

(defn mount-root []
  (reagent/render [#'application]
                  (js/document.getElementById "app")))

(defonce initialized
  (do
    (initialize-routes)))

(defn ^:export run [] (mount-root))

(defn on-js-reload [] (mount-root))
