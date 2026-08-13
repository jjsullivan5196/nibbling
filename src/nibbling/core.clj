(ns nibbling.core
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [hiccup2.core :as h])
  (:import [java.lang AutoCloseable]
           [java.util.concurrent LinkedBlockingQueue TimeUnit]
           [hiccup.util RawString]
           [javafx.application Platform]
           [javafx.scene Scene Node]
           [javafx.scene.layout StackPane]
           [javafx.scene.web WebView WebEngine]
           [javafx.stage Stage])
  (:gen-class))

(declare start-web-shell)

(defmacro run-later
  "Run `forms` on JavaFX thread. Evaluates to a promise containing the
  last evaluated form."
  [& forms]
  (let [pname (gensym)]
    `(let [~pname (promise)]
       (Platform/runLater
        (fn []
          (deliver ~pname (do ~@forms))))
       ~pname)))

;;; Application & state

(def current-web-context (atom nil))

(defn update-greet
  ([] (update-greet {} [:greet/name "Jeff Smith"]))
  ([old-state [n v]] (assoc old-state n v)))

(def css "
body { 
  background: skyblue; 
}

section { 
  display: flex; 
  flex-direction: column; 
  justify-content: center; 
  align-items: center; 
  height: 100vh; 
}
")

(defn render-greet
  [{:greet/keys [name] :as _state}]
  (h/html
   [:div
    [:style (h/raw css)]
    [:section
     [:div [:h1 (str "Hello " name "!")]]
     [:div [:input {:name "greet/name" :value name}]]]]))

(defn restart
  []
  (run-later
   (when @current-web-context
     (.close @current-web-context))
   (reset! current-web-context
           (start-web-shell (Stage.) #'update-greet #'render-greet))))

;;; Framework

(defprotocol IWebContext
  "Interface for defining an application instance."
  (init-page [ctx])
  (render [ctx])
  (receive [ctx cmd])
  (command-loop [ctx]))

(defn morph-page!
  "Morph current DOM tree in `w` to provided `html`."
  [^WebEngine w ^RawString html]
  (.. w
      (executeScript "window")
      (call "morph_body" (object-array [(str html)]))))

(defn init-script
  []
  (str (slurp (io/resource "idiomorph/dist/idiomorph.min.js")) "\n\n"
       (slurp (io/resource "nibbling/bootstrap.js"))))

(defn read-command
  "Deserialize command JSON `s`."
  [s]
  (let [[n v] (json/read-str s)]
    [(keyword n) v]))

(defrecord WebContext [^Stage stage
                       ^WebEngine engine
                       ^LinkedBlockingQueue cmd-queue
                       stopped?
                       state
                       update-fn
                       render-fn]
  IWebContext
  (init-page [ctx]
    (-> (.executeScript engine "window")
        (.setMember "_host" ctx))
    (.executeScript engine (init-script))
    (future (command-loop ctx)))
  (render [_]
    (morph-page! engine (render-fn @state)))
  (receive [_ cmd]
    (future (.add cmd-queue (read-command cmd)))
    nil)
  (command-loop [ctx]
    (while (not @stopped?)
      (let [cmd (.poll cmd-queue 5 TimeUnit/SECONDS)]
        (when cmd
          (swap! state update-fn cmd)
          (run-later (render ctx))))))

  AutoCloseable
  (close [ctx]
    (reset! stopped? true)))

(defn start-web-shell
  "Start a new WebView."
  ^WebContext [^Stage stage update-fn render-fn]
  (let [cmd-queue ^LinkedBlockingQueue (LinkedBlockingQueue.)
        webview   ^WebView (WebView.)
        engine    ^WebEngine (.getEngine webview)
        pane      ^StackPane (StackPane. (into-array Node [webview]))
        scene     ^Scene (Scene. pane 1024 768)
        context   ^WebContext (->WebContext stage engine cmd-queue (atom false) (atom (update-fn)) update-fn render-fn)]
    (init-page context)
    (doto stage
      (.setTitle "")
      (.setScene scene)
      (.show))
    context))

(defn -main
  [args]
  (Platform/startup (constantly nil))
  (restart))

(comment
  (Platform/startup #(Platform/setImplicitExit false))

  (restart)

  (run-later (render @current-web-context))
   
  #_...)
