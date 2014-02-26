(ns nightcode.sandbox
  (:require [clojure.java.io :as io]
            [nightcode.utils :as utils]))

; filesystem

(defn get-dir
  []
  (System/getProperty "SandboxDirectory"))

(defn get-path
  [& dirs]
  (let [home (System/getProperty "user.home")
        dir (get-dir)]
    (when (and home dir)
      (.getCanonicalPath (apply io/file home dir dirs)))))

(defn get-env
  []
  (when-let [path (get-path ".lein")]
    (into-array String [(str "LEIN_HOME=" path)])))

(defn add-dir
  [args]
  (if-let [dir (get-dir)]
    (concat [(first args) (str "-DSandboxDirectory=" dir)] (rest args))
    args))

(defn set-temp-dir!
  []
  (when-let [dir (get-path ".temp")]
    (-> dir io/file .mkdir)
    (System/setProperty "java.io.tmpdir" dir)))

(defn create-profiles-clj!
  []
  (let [profiles-clj (get-path ".lein" "profiles.clj")
        m2 (get-path ".m2")
        tmp (get-path ".temp")]
    (when (and profiles-clj m2 tmp (not (.exists (io/file profiles-clj))))
      (doto (io/file profiles-clj)
        (-> .getParentFile .mkdir)
        (spit (pr-str {:user {:local-repo m2
                              :jvm-opts [(str "-Djava.io.tmpdir=" tmp)]}}))))))

; objc

(defn get-objc-client
  []
  (some-> (try (Class/forName "ca.weblite.objc.Client")
            (catch Exception _))
          (.getMethod "getInstance" (into-array Class []))
          (.invoke nil (object-array []))))

(defn base64->nsdata
  [text]
  (some-> (get-objc-client)
          (.sendProxy "NSData" "data" (object-array []))
          (.send "initWithBase64Encoding:" (object-array [text]))))

(defn write-file-permission!
  [path]
  (some-> (get-objc-client)
          (.sendProxy "NSURL" "fileURLWithPath:" (object-array [path]))
          (.sendProxy "bookmarkDataWithOptions:includingResourceValuesForKeys:relativeToURL:error:"
            (object-array [2048 nil nil nil]))
          (.sendString "base64Encoding" (object-array []))))

(defn read-file-permission!
  [text]
  (some-> (get-objc-client)
          (.sendProxy "NSURL" "URLByResolvingBookmarkData:options:relativeToURL:bookmarkDataIsStale:error:"
            (object-array [(base64->nsdata text) 1024 nil false nil]))
          (.send "startAccessingSecurityScopedResource" (object-array []))))

(defn read-file-permissions!
  []
  (doseq [[path text] (utils/read-pref :permission-map)]
    (read-file-permission! text)))

(defn add-to-permission-map!
  [path]
  (some->> (write-file-permission! path)
           (assoc (utils/read-pref :permission-map) path)
           (utils/write-pref! :permission-map)))

(defn remove-from-permission-map!
  [path]
  (some->> (dissoc (utils/read-pref :permission-map) path)
           (utils/write-pref! :permission-map)))

(defn update-permission-map!
  [k path]
  (some-> (utils/read-pref k)
          remove-from-permission-map!)
  (add-to-permission-map! path))
