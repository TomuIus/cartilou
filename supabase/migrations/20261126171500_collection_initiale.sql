-- =====================================================================
-- Cartilou — schéma initial : la collection de cartes
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), dans le projet partagé avec Canapilou.
-- Ce fichier ne touche à aucune table de Canapilou : tout est rangé dans
-- un schéma à part, « cartilou ».
--
-- APRÈS l'exécution : ajouter « cartilou » à la liste « Exposed schemas »
-- dans les réglages de l'API du tableau de bord (sinon l'appli ne peut
-- pas lire ces tables).
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Le schéma « cartilou » : un espace séparé dans la base, qui regroupe
--    les tables de Cartilou (comme un dossier).
-- ---------------------------------------------------------------------
create schema cartilou;


-- ---------------------------------------------------------------------
-- 2. Table « cartes_possedees » : une ligne par couple carte–version
--    possédé par un utilisateur, avec le nombre d'exemplaires.
--
-- Les informations affichées (nom, image, rareté…) ne sont pas copiées
-- ici : l'appli les lit chez TCGdex. On garde seulement l'identifiant
-- de la carte et, pour pouvoir classer et compter directement dans la
-- base, ceux de son extension et de sa série (décision du 26/11/2026).
--
-- Une ligne n'existe que si l'on possède au moins un exemplaire :
-- retirer le dernier exemplaire supprime la ligne.
-- Supprimer un compte supprime sa collection.
-- ---------------------------------------------------------------------
create table cartilou.cartes_possedees (
  utilisateur_id  uuid        not null default auth.uid()
                              references auth.users (id) on delete cascade,
  carte_id        text        not null,  -- identifiant TCGdex (ex. « swsh3-136 »)
  version         text        not null check (version in
                    ('normale', 'reverse', 'holo', 'premiere_edition')),
  quantite        integer     not null check (quantite > 0),
  extension_id    text        not null,  -- extension TCGdex (ex. « swsh3 »)
  serie_id        text        not null,  -- série TCGdex (ex. « swsh »)
  ajoutee_le      timestamptz not null default now(),
  modifiee_le     timestamptz not null default now(),
  primary key (utilisateur_id, carte_id, version)
);

-- Index : accélère les regroupements par extension pour un utilisateur
-- (« Ma collection », nombre de cartes possédées par extension).
create index cartes_possedees_par_extension
  on cartilou.cartes_possedees (utilisateur_id, extension_id);


-- ---------------------------------------------------------------------
-- 3. Date de modification tenue à jour automatiquement.
--
-- Un « trigger » (déclencheur) lance une fonction à chaque modification
-- d'une ligne : ici, elle met « modifiee_le » à l'heure actuelle.
-- « set search_path = '' » : la fonction n'utilise que des noms
-- complets, ce que recommande Supabase pour la sécurité.
-- ---------------------------------------------------------------------
create function cartilou.maj_modifiee_le()
  returns trigger
  language plpgsql
  set search_path = ''
as $$
begin
  new.modifiee_le := now();
  return new;
end;
$$;

create trigger cartes_possedees_modifiee_le
  before update on cartilou.cartes_possedees
  for each row execute function cartilou.maj_modifiee_le();


-- ---------------------------------------------------------------------
-- 4. Accès depuis l'API (« grants »), comme pour Canapilou : rien pour
--    le rôle « anon » (visiteur non connecté) ; les utilisateurs
--    connectés gèrent leur collection (les règles de la partie 5 les
--    limitent à leurs propres lignes).
-- ---------------------------------------------------------------------
grant usage on schema cartilou to authenticated;
grant select, insert, update, delete on cartilou.cartes_possedees to authenticated;


-- ---------------------------------------------------------------------
-- 5. Sécurité ligne par ligne (« Row Level Security »).
--
-- Une fois activée, la table refuse tout accès sauf ce qu'autorisent
-- les règles ci-dessous : chacun ne voit et ne modifie que ses propres
-- cartes. « (select auth.uid()) » est calculé une seule fois par
-- requête (recommandation de Supabase).
-- ---------------------------------------------------------------------
alter table cartilou.cartes_possedees enable row level security;

create policy "Voir ses propres cartes"
  on cartilou.cartes_possedees for select to authenticated
  using ((select auth.uid()) = utilisateur_id);

create policy "Ajouter ses propres cartes"
  on cartilou.cartes_possedees for insert to authenticated
  with check ((select auth.uid()) = utilisateur_id);

create policy "Modifier ses propres cartes"
  on cartilou.cartes_possedees for update to authenticated
  using ((select auth.uid()) = utilisateur_id)
  with check ((select auth.uid()) = utilisateur_id);

create policy "Retirer ses propres cartes"
  on cartilou.cartes_possedees for delete to authenticated
  using ((select auth.uid()) = utilisateur_id);
