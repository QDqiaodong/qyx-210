package com.example.seatstats.service;

import com.example.seatstats.dto.VoyageDTO;
import com.example.seatstats.entity.JacketCount;
import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Voyage;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.JacketCountRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 排班（班次）领域服务。
 *
 * <p>救生衣短缺时的排班约束：</p>
 * <ul>
 *   <li>某航线救生衣实点<strong>少于额定</strong>期间，不能再给这条航线新排
 *       还没开航的班次（创建即拒绝），不是只在旁边写个短缺数字还继续放行；</li>
 *   <li>已经排好、开航时刻还没到的班次，由 {@link JacketCountService} 在清点保存的
 *       同一事务内标成“缺衣待补”；已经开航的班次不改；</li>
 *   <li>实点补到不少于额定后，新班可以再排。</li>
 * </ul>
 */
@Service
public class VoyageService {

    public static final String ROUTE_STATUS_ACTIVE = "ACTIVE";

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RouteRepository routeRepository;
    private final VoyageRepository voyageRepository;
    private final JacketCountRepository jacketCountRepository;

    public VoyageService(RouteRepository routeRepository,
                         VoyageRepository voyageRepository,
                         JacketCountRepository jacketCountRepository) {
        this.routeRepository = routeRepository;
        this.voyageRepository = voyageRepository;
        this.jacketCountRepository = jacketCountRepository;
    }

    /** 当前时间来源，测试可替换成固定时钟。 */
    private Supplier<LocalDateTime> clock = LocalDateTime::now;

    public void setClock(Supplier<LocalDateTime> clock) {
        this.clock = clock != null ? clock : LocalDateTime::now;
    }

    /**
     * 新排一个班次。
     *
     * <p>创建时在航线行悲观锁内核对最新清点结果：只要该航线当前实点少于额定，
     * 新班一律排不上去。开航时刻必须还没到——已经过去的时刻不允许新建。</p>
     */
    @Transactional
    public VoyageDTO createVoyage(VoyageDTO dto) {
        LocalDateTime now = clock.get();

        // 锁航线行：与正在提交的清点串行，避免“清点短缺与新班放行”交错出中间态。
        Route route = routeRepository.findByIdForUpdate(dto.getRouteId())
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));

        if (!ROUTE_STATUS_ACTIVE.equals(route.getStatus())) {
            throw new BusinessException(400, "航线已停运，不能新排班次；如需排班请先复航");
        }

        if (dto.getDepartureTime() == null) {
            throw new BusinessException(400, "开航时刻不能为空");
        }
        if (!dto.getDepartureTime().isAfter(now)) {
            throw new BusinessException(400, "开航时刻已过，不能新排已经过去的班次");
        }

        // 核心约束：实点少于额定时，新班不能再排上去。
        Optional<JacketCount> count = jacketCountRepository.findByRouteId(route.getId());
        if (count.isPresent() && count.get().isShortage()) {
            JacketCount c = count.get();
            throw new BusinessException(409,
                    "该航线救生衣短缺（额定 " + c.getRequiredCount() + " 件、实点 "
                            + c.getActualCount() + " 件），缺衣补齐前不能新排班次");
        }

        Voyage voyage = new Voyage();
        voyage.setVoyageCode(resolveVoyageCode(dto.getVoyageCode(), route.getRouteCode()));
        voyage.setRoute(route);
        voyage.setDepartureTime(dto.getDepartureTime());
        voyage.setVesselName(dto.getVesselName());
        voyage.setRemark(dto.getRemark());
        // 新班一律按正常创建；若稍后清点出短缺，由清点事务把开航时刻未到的班次联动标缺。
        voyage.setStatus(Voyage.STATUS_NORMAL);

        Voyage saved;
        try {
            saved = voyageRepository.saveAndFlush(voyage);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException(400, "班次编号已存在");
        }

        return toDTO(saved, now);
    }

    /**
     * 排班页：全部班次按开航时刻升序。
     */
    @Transactional(readOnly = true)
    public List<VoyageDTO> listVoyages() {
        LocalDateTime now = clock.get();
        return voyageRepository.findAllByOrderByDepartureTimeAscIdAsc().stream()
                .map(v -> toDTO(v, now))
                .collect(Collectors.toList());
    }

    /**
     * 单航线班次。
     */
    @Transactional(readOnly = true)
    public List<VoyageDTO> listByRoute(Long routeId) {
        if (!routeRepository.existsById(routeId)) {
            throw new BusinessException(404, "航线不存在");
        }
        LocalDateTime now = clock.get();
        return voyageRepository.findByRouteIdOrderByDepartureTimeAscIdAsc(routeId).stream()
                .map(v -> toDTO(v, now))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteVoyage(Long id) {
        if (!voyageRepository.existsById(id)) {
            throw new BusinessException(404, "班次不存在");
        }
        voyageRepository.deleteById(id);
    }

    private String resolveVoyageCode(String requested, String routeCode) {
        String code = (requested == null || requested.isBlank())
                ? "V-" + routeCode + "-" + LocalDateTime.now().format(CODE_TIME_FORMAT)
                        + "-" + (int) (Math.random() * 9000 + 1000)
                : requested.trim();
        if (voyageRepository.existsByVoyageCode(code)) {
            throw new BusinessException(400, "班次编号已存在：" + code);
        }
        return code;
    }

    private VoyageDTO toDTO(Voyage voyage, LocalDateTime now) {
        VoyageDTO dto = new VoyageDTO();
        dto.setId(voyage.getId());
        dto.setVoyageCode(voyage.getVoyageCode());
        dto.setRouteId(voyage.getRoute().getId());
        dto.setRouteCode(voyage.getRoute().getRouteCode());
        dto.setRouteName(voyage.getRoute().getRouteName());
        dto.setDepartureTime(voyage.getDepartureTime());
        dto.setVesselName(voyage.getVesselName());
        dto.setStatus(voyage.getStatus());
        dto.setRemark(voyage.getRemark());
        dto.setCreatedAt(voyage.getCreatedAt());
        // 开航时刻已过即视为已开航（展示用）。已开航班次不会被清点联动修改。
        dto.setDeparted(!voyage.getDepartureTime().isAfter(now));
        return dto;
    }
}
