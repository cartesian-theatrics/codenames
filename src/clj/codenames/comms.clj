(ns codenames.comms
  (:require
   [codenames.db :as db]
   [codenames.facts :as facts]
   [codenames.constants.ui-idents :as idents]
   [codenames.comms-common :as comms-common]
   [codenames.queries :as queries]
   [clojure.core.async :refer [go-loop <! put!]]
   [datahike.core :as d]
   [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
   [taoensso.sente :as sente]
   [taoensso.timbre :as timbre :refer [info warn]]
   [hitchhiker.tree.key-compare :refer [IKeyCompare]]
   [hitchhiker.tree.node :as n]))

(extend-protocol IKeyCompare
  java.util.Date
  (-compare [^java.util.Date key1 key2]
    (if (instance? java.util.Date key2)
      (.compareTo key1 key2)
      (try
        (compare key1 key2)
        (catch ClassCastException e
          (- (n/-order-on-edn-types key2)
             (n/-order-on-edn-types key1)))))))

(defonce sente-vars
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter) {:packer (comms-common/->TransitPacker)})]
    (def ^{:dynamic true} *ring-ajax-post* ajax-post-fn)
    (def ^{:dynamic true} *ring-ajax-get-or-ws-handshake* ajax-get-or-ws-handshake-fn)
    (def ^{:dynamic true} *ch-chsk*                       ch-recv) ; ChannelSocket's receive channel
    (def ^{:dynamic true} *chsk-send!*                    send-fn) ; ChannelSocket's send API fn
    (def ^{:dynamic true} *connected-uids*                connected-uids) ; Watchable, read-only atom
    ))

(def ^:dynamic *current-uid* nil)

(defmulti client-event (comp first :event))

(defmethod client-event :default
  [{:keys [event]}]
  (warn "Unknown client event: " (first event)))

(defn insert-facts! [username groupname datoms tx-meta group-update?]
  (let [user-facts  (filter (comp db/user-attributes :a) datoms)
        group-facts (filter (comp db/group-attributes :a) datoms)
        user-conn   (facts/key->conn username facts/initial-user-facts)
        group-conn  (facts/key->conn groupname facts/initial-group-facts)]
    (when (seq user-facts)
      (facts/insert-facts! user-conn user-facts)
      (when group-update?
        (doseq [{other-username :user/name} (queries/groupname->users @group-conn groupname)]
          (facts/insert-facts! (facts/key->conn other-username facts/initial-user-facts) user-facts)
          (when (not= other-username username)
            (*chsk-send!* other-username [::facts {:datoms user-facts
                                                   :tx-meta tx-meta}])))))
    (when (seq group-facts)
      (facts/insert-facts! group-conn group-facts)
      (if *chsk-send!*
        (doseq [{other-username :user/name} (queries/groupname->users @group-conn groupname)
                :when                       (not= other-username username)]
          (*chsk-send!* other-username [::facts {:datoms group-facts
                                                 :tx-meta tx-meta}]))
        (warn "Undefined var: *chsk-send!*")))))

(defmethod client-event :chsk/ws-ping
  [{username :uid}]
  (let [conn       (facts/key->conn username facts/initial-user-facts)
        session    (d/entity @conn [:swig/ident idents/session])
        groupname  (:session/groupname session)
        group-conn (facts/key->conn groupname facts/initial-group-facts)
        facts      [[:db/add [:user/name username] :user/last-seen (java.util.Date.)]]]
    (d/transact! group-conn facts)
    (doseq [{other-username :user/name} (queries/groupname->users @group-conn groupname)]
      (*chsk-send!* other-username [::facts facts]))
    (info "ping event")))

(defmethod client-event ::facts
  [{[_ {:keys [gid datoms tx-meta]}] :event uid :uid}]
  (insert-facts! uid gid datoms tx-meta false))

(defmethod client-event ::group-facts
  [{[_ {:keys [gid datoms tx-meta]}] :event uid :uid}]
  (insert-facts! uid gid datoms tx-meta true))

(defn init-sente! []
  (info "starting client receive loop")
  (go-loop []
    (let [x (<! *ch-chsk*)
          event (:event x)]
      (if (= event ::stop)
        (info "Exiting")
        (do (client-event x)
            (recur))))))
(comment 
  (put! *ch-chsk* {:event ::stop}))

(timbre/set-level! :info)

