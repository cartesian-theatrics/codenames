(ns codenames.subs.game
  (:require
   [swig.macros :refer [def-sub def-pull-sub]]))

(def-sub ::cards
  [:find (pull ?card [:codenames.character-card/color
                      :codenames.character-card/position
                      :codenames.character-card/word])
   :in $
   :where
   [?card :codenames.character-card/color]])

(def-sub ::word-cards
  [:find (pull ?card [:codenames.word-card/position
                      :codenames.word-card/word
                      :codenames.word-card/character-card])
   :in $ ?game-id
   :where
   [?card :codenames.word-card/word]
   [?game-id :game/current-round ?round-id]
   [?card :codenames.piece/round ?round-id]])

(def-sub ::player-type
  [:find ?player-type .
   :in $ ?game-id
   :where
   [?sid :session/user ?uid]
   [?game-id :game/teams ?tid]
   [?tid :codenames.team/players ?pid]
   [?pid :codenames.player/user ?uid]
   [?pid :codenames.player/type ?player-type]])

(def-sub ::red-cards-remaining
  [:find ?rem .
   :in $ ?game-id
   :where
   [?game-id :game/current-round ?round-id]
   [?round-id :codenames.round/red-cards-count ?rem]])

(def-sub ::blue-cards-remaining
  [:find ?rem .
   :in $ ?game-id
   :where
   [?game-id :game/current-round ?round-id]
   [?round-id :codenames.round/blue-cards-count ?rem]])

(def-sub ::current-team
  [:find (pull ?tid [:codenames.team/color
                     :codenames.team/name]) .
   :in $ ?game-id
   :where
   [?game-id :game/current-round ?round-id]
   [?round-id :codenames.round/current-team ?tid]])

(def-pull-sub ::character-card
  [:codenames.character-card/role
   :codenames.character-card/played?])

(def-sub ::game-over
  [:find [?team-id ?color]
   :in $ ?game-id
   :where
   [?game-id :game/teams]
   [?id :codenames.character-card/role]
   [?team-id :codenames.team/color]
   [?id :codenames.character-card/played?]
   (or (and [?game-id :game/teams ?team-id]
            [?id :codenames.character-card/role]
            [?game-id :game/current-round ?round-id]
            [?team-id :codenames.team/color ?color]
            (or (and [?round-id :codenames.round/blue-cards-count 0]
                     [?team-id :codenames.team/color :blue])
                (and [?round-id :codenames.round/red-cards-count 0]
                     [?team-id :codenames.team/color :red])))
       (and [?game-id :game/teams ?team-id]
            [?game-id :game/current-round ?round-id]
            [?team-id :codenames.team/color ?color]
            [?id :codenames.character-card/played? true]
            [?id :codenames.character-card/role :assassin]
            [?id :codenames.piece/round ?round-id]
            [?round-id :codenames.round/current-team ?team-id]))])

(def-sub ::get-browser-src
  [:find ?src .
   :in $ ?id
   :where
   [?id :html.iframe/src ?src]])

(def-sub ::current-turn
  [:find (pull ?turn-id [:codenames.turn/number
                         :codenames.turn/word
                         :codenames.turn/submitted?]) .
   :in $ ?game-id
   :where
   [?game-id :game/current-round ?round-id]
   [?round-id :codenames.round/current-turn ?turn-id]])
