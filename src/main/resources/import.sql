-- This file allow to write SQL commands that will be emitted in test and dev.
-- The commands are commented as their support depends of the database
-- insert into myentity (id, field) values(1, 'field-1');
-- insert into myentity (id, field) values(2, 'field-2');
-- insert into myentity (id, field) values(3, 'field-3');
-- alter sequence myentity_seq restart with 4;

-- Insere dados na tabela FichaCachorro
insert into FichaCachorro (descricaoHistoria, temperamentoPrincipal, habilidadesEspeciais) values(
                                                                                                     'Resgatada de uma situação de maus-tratos. Ela é tímida com estranhos, mas extremamente leal e carinhosa com quem confia. Precisa de um lar paciente.',
                                                                                                     'Tímida, mas leal',
                                                                                                     'Excelente farejadora, aprende comandos rapidamente'
                                                                                                 );

insert into FichaCachorro (descricaoHistoria, temperamentoPrincipal, habilidadesEspeciais) values(
                                                                                                     'Encontrado abandonado na rua. Este filhote é muito enérgico e brincalhão, adora correr e explorar. Ideal para uma família ativa com espaço para ele se exercitar.',
                                                                                                     'Brincalhão, enérgico',
                                                                                                     'Ótimo com crianças, adora buscar bolinhas'
                                                                                                 );

insert into FichaCachorro (descricaoHistoria, temperamentoPrincipal, habilidadesEspeciais) values(
                                                                                                     'Cachorro de porte grande que precisa de um espaço com quintal para brincar. Ele se dá bem com outros cães e gatos, mas tem medo de barulhos altos como fogos de artifício.',
                                                                                                     'Calmo, sociável',
                                                                                                     'Guardião, protetor do lar'
                                                                                                 );

insert into FichaCachorro (descricaoHistoria, temperamentoPrincipal, habilidadesEspeciais) values(
                                                                                                     'Cachorro resgatado após ser ferido em um atropelamento. Ele se recuperou bem e agora adora carinho e colo. É um companheiro ideal para uma pessoa que mora sozinha.',
                                                                                                     'Afetuoso, tranquilo',
                                                                                                     'Não solta pelos'
                                                                                                 );

insert into FichaCachorro (descricaoHistoria, temperamentoPrincipal, habilidadesEspeciais) values(
                                                                                                     'Cachorro idoso que foi entregue ao abrigo por seu tutor que não podia mais cuidar dele. Ele é muito dócil e adora uma boa soneca. Ideal para um lar tranquilo.',
                                                                                                     'Dócil, preguiçoso',
                                                                                                     'Não late muito'
                                                                                                 );

-- Insere dados na tabela Cachorro
insert into Cachorro (nome, dataDeNascimento, localDeResgate, ficha_cachorro_id) values('Luna', '2023-05-15', 'São Paulo', 1);
insert into Cachorro (nome, dataDeNascimento, localDeResgate, ficha_cachorro_id) values('Spike', '2024-01-20', 'Rio de Janeiro', 2);
insert into Cachorro (nome, dataDeNascimento, localDeResgate, ficha_cachorro_id) values('Max', '2022-03-05', 'Belo Horizonte', 3);
insert into Cachorro (nome, dataDeNascimento, localDeResgate, ficha_cachorro_id) values('Pipoca', '2023-08-10', 'Salvador', 4);
insert into Cachorro (nome, dataDeNascimento, localDeResgate, ficha_cachorro_id) values('Toby', '2018-09-01', 'Curitiba', 5);

-- Insere dados na tabela Raca
insert into Raca (nome, descricao) values('SRD', 'Sem Raça Definida. Cães únicos e cheios de personalidade.');
insert into Raca (nome, descricao) values('Golden Retriever', 'Raça de grande porte, conhecida por sua inteligência e temperamento gentil.');
insert into Raca (nome, descricao) values('Poodle', 'Cães de companhia inteligentes, de pelagem hipoalergênica e que se adaptam bem a apartamentos.');
insert into Raca (nome, descricao) values('Shih Tzu', 'Raça pequena e de temperamento dócil, perfeita para viver em ambientes internos.');
insert into Raca (nome, descricao) values('Pastor Alemão', 'Cães de trabalho inteligentes e confiáveis, frequentemente usados como cães de guarda ou serviço.');

-- Insere dados na tabela Adocao
insert into Adocao (dataSolicitacao, justificativa, status, cachorro_id) values(
                                                                                   '2024-09-20',
                                                                                   'Sempre quis um companheiro para me fazer companhia no meu apartamento, e Luna parece a cachorra perfeita para mim.',
                                                                                   'Pendente',
                                                                                   1
                                                                               );

insert into Adocao (dataSolicitacao, justificativa, status, cachorro_id) values(
                                                                                   '2024-09-21',
                                                                                   'Minha família adora cachorros e temos um grande quintal para o Max. Queremos dar um novo lar a ele.',
                                                                                   'Aprovada',
                                                                                   3
                                                                               );

insert into Adocao (dataSolicitacao, justificativa, status, cachorro_id) values(
                                                                                   '2024-09-22',
                                                                                   'Quero um cachorro que me ajude a me exercitar e passar mais tempo ao ar livre. Spike parece o ideal para a minha vida ativa.',
                                                                                   'Pendente',
                                                                                   2
                                                                               );

insert into Adocao (dataSolicitacao, justificativa, status, cachorro_id) values(
                                                                                   '2024-09-22',
                                                                                   'Sempre tive um carinho especial por cachorros idosos. Gostaria de dar a Toby um lar tranquilo e amoroso para seus últimos anos.',
                                                                                   'Pendente',
                                                                                   5
                                                                               );

-- Associações adoção-raça (Many-to-Many)
insert into adocao_raca (adocao_id, raca_id) values (1, 1), (1, 3); -- Luna, SRD e Poodle
insert into adocao_raca (adocao_id, raca_id) values (2, 5);          -- Max, Pastor Alemão
insert into adocao_raca (adocao_id, raca_id) values (3, 1);          -- Spike, SRD
insert into adocao_raca (adocao_id, raca_id) values (4, 4);          -- Pipoca, Shih Tzu
